#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Local inference CLI for ETC 5-min flow forecasting (12 steps = 1 hour).

Data source:
  - full.jsonl produced by build_etc_flow_5min_dataset.py (one series per CP×FXLX)

Output:
  - future 12 steps (5-min) predictions for a given checkpointId + direction.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import numpy as np


def _add_time_moe_to_syspath() -> None:
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


def parse_time_to_bucket_idx(raw: str) -> int | None:
    """
    Parse time like:
      - 2023-12-25 00:00:00
      - 2023-12-25 00:00
      - 2023/12/25 0:00:01
    Return 5-min bucket idx from 2023-12-01 00:00.
    """
    s = (raw or "").strip()
    if not s:
        return None
    parts = s.replace("T", " ").split()
    if not parts:
        return None
    date_part = parts[0]
    time_part = parts[1] if len(parts) > 1 else "00:00:00"

    date_sep = "/" if "/" in date_part else "-"
    d = date_part.split(date_sep)
    if len(d) != 3:
        return None
    try:
        y = int(d[0])
        m = int(d[1])
        day = int(d[2])
    except Exception:
        return None
    if (y, m) != (2023, 12) or not (1 <= day <= 31):
        return None

    t = time_part.split(":")
    try:
        hh = int(t[0]) if len(t) > 0 and t[0] else 0
        mm = int(t[1]) if len(t) > 1 and t[1] else 0
    except Exception:
        return None
    if hh < 0 or hh > 23 or mm < 0 or mm > 59:
        return None

    minutes = (day - 1) * 24 * 60 + hh * 60 + mm
    return int(minutes // 5)


_BASE_START = datetime(2023, 12, 1, 0, 0, 0)


def bucket_idx_to_time_str(bucket_idx: int) -> str:
    # Supports crossing month boundary (e.g. idx=8928 -> 2024-01-01 00:00:00)
    dt = _BASE_START + timedelta(minutes=int(bucket_idx) * 5)
    return dt.strftime("%Y-%m-%d %H:%M:%S")


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
    parser = argparse.ArgumentParser("ETC 5-min flow prediction (Time-MoE)")
    parser.add_argument("--checkpoint_id", type=str, required=True, help="Checkpoint code, e.g. CP016")
    parser.add_argument("--direction", type=str, required=True, help="FXLX direction: 1=进城, 2=出城")
    parser.add_argument(
        "--data_path",
        type=str,
        default="Time-MoE/datasets/etc_flow_5min/full.jsonl",
        help="Path to full.jsonl (default: Time-MoE/datasets/etc_flow_5min/full.jsonl)",
    )
    parser.add_argument(
        "--scales_path",
        type=str,
        default="Time-MoE/datasets/etc_flow_5min/scales.json",
        help="Path to scales.json (optional, used for scales normalization).",
    )
    parser.add_argument(
        "--model_path",
        "-m",
        type=str,
        default="Maple728/TimeMoE-50M",
        help="Model path (HF repo id or local folder). Default: Maple728/TimeMoE-50M",
    )
    parser.add_argument("--context_length", "-c", type=int, default=512)
    parser.add_argument("--prediction_length", "-p", type=int, default=12)
    parser.add_argument(
        "--at_time",
        type=str,
        default="",
        help="Prediction start time (YYYY-MM-DD HH:MM[:SS]) in 2023-12. Default: end of series.",
    )
    parser.add_argument(
        "--norm",
        choices=["context", "scales"],
        default="context",
        help="Normalization mode: context=per-window mean/std; scales=use scales.json full mean/std.",
    )
    parser.add_argument(
        "--scales_split",
        choices=["full", "train", "val"],
        default="full",
        help="Which split stats to use when norm=scales. Default: full.",
    )
    parser.add_argument(
        "--clip-nonneg",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Clip predictions to >=0 (default: true). Use --no-clip-nonneg to disable.",
    )
    parser.add_argument(
        "--round",
        action="store_true",
        help="Round output to integers (after clipping).",
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
        "--json",
        action="store_true",
        help="Output JSON instead of pretty text.",
    )
    args = parser.parse_args()

    cp = args.checkpoint_id.strip()
    fx = args.direction.strip()
    if fx not in ("1", "2"):
        raise SystemExit("--direction must be '1' or '2'")
    series_id = f"{cp}#fx={fx}"

    data_rows = read_jsonl(Path(args.data_path))
    target_row = next((r for r in data_rows if str(r.get("id") or "") == series_id), None)
    if target_row is None:
        raise SystemExit(f"Series not found in {args.data_path}: {series_id}")

    seq_list = target_row.get("sequence")
    if not isinstance(seq_list, list) or not seq_list:
        raise SystemExit(f"Invalid sequence for series: {series_id}")
    seq = np.asarray(seq_list, dtype=np.float32)

    context_length = int(args.context_length)
    pred_len = int(args.prediction_length)
    if context_length <= 0 or pred_len <= 0:
        raise SystemExit("context_length and prediction_length must be > 0")

    if args.at_time:
        at_idx = parse_time_to_bucket_idx(args.at_time)
        if at_idx is None:
            raise SystemExit(f"Invalid --at_time: {args.at_time}")
    else:
        at_idx = int(seq.shape[0])  # predict after the last observed bucket

    if at_idx < context_length:
        raise SystemExit(
            f"Not enough history: at_idx={at_idx}, context_length={context_length}. "
            f"Need at least {context_length} points before at_time."
        )

    # Build context window
    ctx = seq[at_idx - context_length : at_idx].astype(np.float32)

    # Determine normalization parameters
    if args.norm == "context":
        mean = float(ctx.mean(dtype=np.float64))
        std = float(ctx.std(dtype=np.float64))
        if std == 0.0:
            std = 1.0
    else:
        scales_path = Path(args.scales_path)
        scales: dict[str, Any] = {}
        if scales_path.exists():
            scales = json.loads(scales_path.read_text(encoding="utf-8"))
        series_meta = (scales.get("series") or {}).get(series_id) or {}
        split_meta = series_meta.get(args.scales_split) or {}
        mean = float(split_meta.get("mean") or 0.0)
        std = float(split_meta.get("std") or 1.0)
        if std == 0.0:
            std = 1.0

    norm_ctx = (ctx - mean) / std

    # Resolve device
    import torch

    if args.device == "cpu":
        device = "cpu"
    elif args.device == "cuda":
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
    else:
        device = "cuda:0" if torch.cuda.is_available() else "cpu"

    model = load_model(args.model_path, device=device, dtype=args.dtype)

    x = torch.from_numpy(norm_ctx[None, :]).to(device).to(model.dtype)
    with torch.no_grad():
        out = model.generate(x, max_new_tokens=pred_len)
    if out.ndim == 3:
        out = out.squeeze(-1)
    pred_norm = out[:, -pred_len:].to(torch.float32).cpu().numpy()[0]
    preds = pred_norm * std + mean

    if args.clip_nonneg:
        preds = np.maximum(preds, 0.0)
    if args.round:
        preds = np.rint(preds)

    start_time = bucket_idx_to_time_str(at_idx)
    pred_times = [bucket_idx_to_time_str(at_idx + i) for i in range(pred_len)]

    if args.json:
        payload = {
            "seriesId": series_id,
            "checkpointId": cp,
            "direction": fx,
            "startTime": start_time,
            "freqMin": 5,
            "contextLength": context_length,
            "predictionLength": pred_len,
            "norm": args.norm,
            "mean": float(mean),
            "std": float(std),
            "values": [float(x) for x in preds.tolist()],
            "times": pred_times,
        }
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return

    print("=" * 72)
    print("ETC 5-min flow prediction (12 steps = 1 hour)")
    print(f"  series: {series_id} (checkpoint={cp}, direction={fx})")
    print(f"  model:  {args.model_path}")
    print(f"  device={device}, dtype={args.dtype}, norm={args.norm}")
    print(f"  start:  {start_time}")
    print("-" * 72)
    for t, v in zip(pred_times, preds.tolist()):
        print(f"{t}  {v:.4f}" if not args.round else f"{t}  {int(v)}")
    print("=" * 72)


if __name__ == "__main__":
    main()


