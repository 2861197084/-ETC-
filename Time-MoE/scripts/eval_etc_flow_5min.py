#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Offline evaluation for ETC 5-min flow forecasting (12 steps = 1 hour).

It reads val.jsonl produced by:
  Time-MoE/scripts/build_etc_flow_5min_dataset.py

and evaluates a given model (pretrained or fine-tuned) via autoregressive generation.
"""

from __future__ import annotations

import argparse
import csv as csv_lib
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

import numpy as np


def _add_time_moe_to_syspath() -> None:
    # Ensure `import time_moe` works when running from repo root:
    # .../Time-MoE/scripts/<this_file> -> add .../Time-MoE to sys.path
    time_moe_root = Path(__file__).resolve().parents[1]
    if str(time_moe_root) not in sys.path:
        sys.path.insert(0, str(time_moe_root))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s:
                continue
            rows.append(json.loads(s))
    return rows


def safe_std(x: np.ndarray) -> float:
    v = float(np.std(x, dtype=np.float64))
    return 1.0 if v == 0.0 else v


@dataclass
class SeriesEvalResult:
    series_id: str
    checkpoint_id: str
    direction: str
    windows: int
    mae: float
    mse: float
    rmse: float


def load_model(model_path: str, device: str, dtype: str):
    import torch
    from transformers import AutoModelForCausalLM

    _add_time_moe_to_syspath()

    torch_dtype: Any
    if dtype == "auto":
        torch_dtype = "auto"
    elif dtype == "fp16":
        torch_dtype = torch.float16
    elif dtype == "bf16":
        torch_dtype = torch.bfloat16
    elif dtype == "fp32":
        torch_dtype = torch.float32
    else:
        raise ValueError(f"Unknown dtype: {dtype}")

    try:
        from time_moe.models.modeling_time_moe import TimeMoeForPrediction

        model = TimeMoeForPrediction.from_pretrained(
            model_path,
            device_map=device,
            torch_dtype=torch_dtype,
        )
    except Exception:
        model = AutoModelForCausalLM.from_pretrained(
            model_path,
            device_map=device,
            torch_dtype=torch_dtype,
            trust_remote_code=True,
        )

    model.eval()
    return model


def main() -> None:
    parser = argparse.ArgumentParser("ETC 5-min flow eval for Time-MoE")
    parser.add_argument(
        "--model_path",
        "-m",
        type=str,
        default="Maple728/TimeMoE-50M",
        help="Model path (HF repo id or local folder). Default: Maple728/TimeMoE-50M",
    )
    parser.add_argument(
        "--val_path",
        type=str,
        default="Time-MoE/datasets/etc_flow_5min/val.jsonl",
        help="Path to val.jsonl (default: Time-MoE/datasets/etc_flow_5min/val.jsonl)",
    )
    parser.add_argument(
        "--scales_path",
        type=str,
        default="Time-MoE/datasets/etc_flow_5min/scales.json",
        help="Path to scales.json (optional, used to locate target_start_idx_in_val).",
    )
    parser.add_argument("--context_length", "-c", type=int, default=512)
    parser.add_argument("--prediction_length", "-p", type=int, default=12)
    parser.add_argument(
        "--eval_stride",
        type=int,
        default=12,
        help="Stride over evaluation start positions (in 5-min steps). Default 12 (=1 hour).",
    )
    parser.add_argument(
        "--max_windows_per_series",
        type=int,
        default=0,
        help="Limit number of eval windows per series (0=all).",
    )
    parser.add_argument(
        "--batch_size",
        type=int,
        default=16,
        help="Eval batch size (for windows). Default 16.",
    )
    parser.add_argument(
        "--norm",
        choices=["context", "series", "none"],
        default="context",
        help="Normalization mode: context=per-window mean/std; series=use val meta mean/std; none=no norm.",
    )
    parser.add_argument(
        "--device",
        choices=["auto", "cuda", "cpu"],
        default="auto",
        help="Device to run model. Default auto.",
    )
    parser.add_argument(
        "--dtype",
        choices=["auto", "fp16", "bf16", "fp32"],
        default="auto",
        help="Model dtype. For 8GB GPU, fp16 is recommended.",
    )
    parser.add_argument(
        "--out_csv",
        type=str,
        default="",
        help="Optional: write per-series metrics to CSV.",
    )
    args = parser.parse_args()

    val_path = Path(args.val_path)
    scales_path = Path(args.scales_path)
    context_length = int(args.context_length)
    pred_len = int(args.prediction_length)
    eval_stride = int(max(1, args.eval_stride))
    batch_size = int(max(1, args.batch_size))

    if context_length <= 0 or pred_len <= 0:
        raise SystemExit("context_length and prediction_length must be > 0")

    val_rows = read_jsonl(val_path)
    scales: dict[str, Any] = {}
    if scales_path.exists():
        try:
            scales = json.loads(scales_path.read_text(encoding="utf-8"))
        except Exception:
            scales = {}

    # Resolve device
    import torch

    if args.device == "cpu":
        device = "cpu"
    elif args.device == "cuda":
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
    else:
        device = "cuda:0" if torch.cuda.is_available() else "cpu"

    model = load_model(args.model_path, device=device, dtype=args.dtype)

    # Global accumulators
    total_abs_err = np.zeros(pred_len, dtype=np.float64)
    total_sq_err = np.zeros(pred_len, dtype=np.float64)
    total_count = 0
    per_series: list[SeriesEvalResult] = []

    for row in val_rows:
        series_id = str(row.get("id") or "")
        checkpoint_id = str(row.get("checkpointId") or "")
        direction = str(row.get("direction") or "")

        seq_list = row.get("sequence")
        if not isinstance(seq_list, list) or len(seq_list) < context_length + pred_len:
            continue
        seq = np.asarray(seq_list, dtype=np.float32)

        # Determine where \"real\" validation starts inside val.jsonl series
        target_start_idx_in_val = 0
        try:
            series_meta = (scales.get("series") or {}).get(series_id) or {}
            target_start_idx_in_val = int((series_meta.get("val") or {}).get("target_start_idx_in_val") or 0)
        except Exception:
            target_start_idx_in_val = 0

        start_t = max(target_start_idx_in_val, context_length)
        end_t = len(seq) - pred_len
        if start_t > end_t:
            continue

        positions = list(range(start_t, end_t + 1, eval_stride))
        if args.max_windows_per_series and args.max_windows_per_series > 0:
            positions = positions[: int(args.max_windows_per_series)]
        if not positions:
            continue

        series_abs_err = np.zeros(pred_len, dtype=np.float64)
        series_sq_err = np.zeros(pred_len, dtype=np.float64)
        series_count = 0

        # series-level mean/std (from val meta)
        series_mean = float((row.get("meta") or {}).get("mean") or 0.0)
        series_std = float((row.get("meta") or {}).get("std") or 1.0)
        if series_std == 0.0:
            series_std = 1.0

        # Batch windows
        for b0 in range(0, len(positions), batch_size):
            batch_pos = positions[b0 : b0 + batch_size]

            ctx = np.stack([seq[t - context_length : t] for t in batch_pos], axis=0).astype(np.float32)
            labels = np.stack([seq[t : t + pred_len] for t in batch_pos], axis=0).astype(np.float32)

            if args.norm == "context":
                mean = ctx.mean(axis=1, keepdims=True, dtype=np.float64).astype(np.float32)
                std = ctx.std(axis=1, keepdims=True, dtype=np.float64).astype(np.float32)
                std[std == 0] = 1.0
                norm_ctx = (ctx - mean) / std
                inv_mean = mean
                inv_std = std
            elif args.norm == "series":
                norm_ctx = (ctx - series_mean) / series_std
                inv_mean = series_mean
                inv_std = series_std
            else:
                norm_ctx = ctx
                inv_mean = 0.0
                inv_std = 1.0

            # Model inference
            x = torch.from_numpy(norm_ctx).to(device).to(model.dtype)
            with torch.no_grad():
                out = model.generate(x, max_new_tokens=pred_len)
            if out.ndim == 3:
                out = out.squeeze(-1)
            pred_norm = out[:, -pred_len:].to(torch.float32).cpu().numpy()

            # Inverse normalization
            if args.norm == "context":
                preds = pred_norm * inv_std + inv_mean
            elif args.norm == "series":
                preds = pred_norm * inv_std + inv_mean
            else:
                preds = pred_norm

            err = preds - labels
            series_abs_err += np.abs(err, dtype=np.float64).sum(axis=0)
            series_sq_err += np.square(err, dtype=np.float64).sum(axis=0)
            series_count += err.shape[0]

        if series_count == 0:
            continue

        total_abs_err += series_abs_err
        total_sq_err += series_sq_err
        total_count += series_count

        mae_h = series_abs_err / series_count
        mse_h = series_sq_err / series_count
        mae = float(mae_h.mean())
        mse = float(mse_h.mean())
        rmse = float(math.sqrt(mse))
        per_series.append(
            SeriesEvalResult(
                series_id=series_id,
                checkpoint_id=checkpoint_id,
                direction=direction,
                windows=series_count,
                mae=mae,
                mse=mse,
                rmse=rmse,
            )
        )

    if total_count == 0:
        raise SystemExit("No evaluation windows produced. Check val.jsonl length and context/pred settings.")

    mae_h = total_abs_err / total_count
    mse_h = total_sq_err / total_count
    mae = float(mae_h.mean())
    mse = float(mse_h.mean())
    rmse = float(math.sqrt(mse))

    print("=" * 72)
    print("ETC 5-min flow eval (12 steps = 1 hour)")
    print(f"  model: {args.model_path}")
    print(f"  val:   {val_path}")
    print(f"  device={device}, dtype={args.dtype}, norm={args.norm}")
    print(f"  windows_total={total_count:,}, series={len(per_series)}")
    print(f"  MAE={mae:.6f}, MSE={mse:.6f}, RMSE={rmse:.6f}")
    print("  MAE by horizon (steps 1..p):")
    print("   " + ", ".join([f"{v:.4f}" for v in mae_h.tolist()]))
    print("=" * 72)

    # Top worst series
    per_series_sorted = sorted(per_series, key=lambda r: r.mae, reverse=True)
    top_k = min(10, len(per_series_sorted))
    print(f"Top-{top_k} worst series by MAE:")
    for r in per_series_sorted[:top_k]:
        print(f"  {r.series_id}  windows={r.windows}  MAE={r.mae:.4f}  RMSE={r.rmse:.4f}")

    if args.out_csv:
        out_csv = Path(args.out_csv)
        out_csv.parent.mkdir(parents=True, exist_ok=True)
        with out_csv.open("w", encoding="utf-8", newline="") as f:
            w = csv_lib.writer(f)
            w.writerow(["series_id", "checkpointId", "direction", "windows", "mae", "mse", "rmse"])
            for r in per_series_sorted:
                w.writerow([r.series_id, r.checkpoint_id, r.direction, r.windows, r.mae, r.mse, r.rmse])
        print(f"[OK] wrote per-series metrics: {out_csv}")


if __name__ == "__main__":
    main()


