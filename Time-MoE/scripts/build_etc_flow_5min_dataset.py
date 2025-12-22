#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Build ETC flow dataset for Time-MoE fine-tuning.

Input:
  - data/expanded/2023-12-*.csv (fields: KKMC, FXLX, GCSJ, ...)

Output (jsonl):
  - full.jsonl : one series per (checkpointId, direction) for the whole month (5-min buckets)
  - train.jsonl: prefix split (default: 2023-12-01..2023-12-24)
  - val.jsonl  : suffix split with optional left context (default: include 512 steps context before 2023-12-25)
  - scales.json: per-series mean/std for full/train/val

Each jsonl line:
  {"id":"CP001#fx=1","checkpointId":"CP001","direction":"1","sequence":[...],"meta":{...}}
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np


# NOTE: Keep this mapping aligned with:
# - data-service/scripts/import_to_hbase.py
# - data-service/scripts/realtime_simulator.py
RAW_KKMC_TO_ID: dict[str, str] = {
    # 苏皖界（省际）
    "徐州市睢宁县G104北京-福州K873江苏徐州-G104-苏皖界省际卡口": "CP001",
    "徐州市铜山县G311徐州-西峡K207江苏徐州-G311-苏皖界省际卡口": "CP002",
    "徐州市睢宁县S252塔双线K56江苏徐州-S252-苏皖界省际卡口": "CP003",
    "徐州市铜山县G206烟台-汕头K816江苏徐州-G206-苏皖界省际卡口": "CP004",
    "徐州市丰县G518518国道K358马楼公路站省际卡口": "CP005",
    "徐州市丰县G237国道237线K148荣庄卡口省际卡口": "CP006",
    # 苏鲁界（省际）
    "徐州市沛县S253郑沛龙线K0江苏徐州-S253-苏鲁界省际卡口": "CP007",
    "徐州市铜山县G104北京-福州K744江苏徐州-G104-苏鲁界省际卡口": "CP008",
    "G3京台高速K731江苏高速五大队江苏徐州-G3-苏鲁界省际卡口": "CP009",
    "徐州市邳州市S250宿邳线K1江苏徐州-S250-苏鲁界省际卡口": "CP010",
    "徐州市邳州市S251枣睢线K5江苏徐州-S251-苏鲁界省际卡口": "CP011",
    "江苏省徐州市新沂市S323连徐线K96瓦窑检查站市际卡口": "CP012",
    # 连云港界（市际）
    "徐州市新沂市S323连徐线K10阿湖卡口-323省道连云港交界市际卡口": "CP013",
    "徐州市铜山县G310连云港-天水K310江苏徐州-G310-苏皖界省际卡口": "CP014",
    # 宿迁界（市际）
    "徐州市新沂市S505505省道K10新沂高速西出口-505省道宿迁界市际卡口": "CP015",
    "江苏省徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口": "CP016",
    "徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口": "CP016",
    "徐州市睢宁县S324燕沭睢线K201省道桑庄王马路路口西侧-向东卡口市际卡口": "CP017",
    "徐州市新沂市G235国道235K10江苏徐州-G235-交界市际卡口": "CP018",
    "徐州市丰县鹿梁路K19丰县梁寨检查站市际卡口": "CP019",
}

VALID_DIRECTIONS = ("1", "2")  # 1=进城, 2=出城


@dataclass(frozen=True)
class MonthSpec:
    year: int
    month: int
    days: int

    @property
    def steps_per_day(self) -> int:
        return 24 * 60 // 5

    @property
    def total_steps(self) -> int:
        return self.days * self.steps_per_day


MONTH_2023_12 = MonthSpec(year=2023, month=12, days=31)


def _repo_root() -> Path:
    # .../Time-MoE/scripts/<this_file> -> repo root is two levels up from Time-MoE/
    return Path(__file__).resolve().parents[2]


def parse_gcsj_to_bucket_idx(raw: str, month: MonthSpec) -> int | None:
    """
    Parse GCSJ formats like:
      - 2023/12/1 0:00:01
      - 2023-12-01 06:49:01
    Return 5-min bucket index in [0, month.total_steps).
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

    if y != month.year or m != month.month or day < 1 or day > month.days:
        return None

    t = time_part.split(":")
    try:
        hh = int(t[0]) if len(t) > 0 and t[0] else 0
        mm = int(t[1]) if len(t) > 1 and t[1] else 0
    except Exception:
        return None

    if hh < 0 or hh > 23 or mm < 0 or mm > 59:
        return None

    minutes_from_month_start = (day - 1) * 24 * 60 + hh * 60 + mm
    bucket = minutes_from_month_start // 5
    if bucket < 0 or bucket >= month.total_steps:
        return None
    return int(bucket)


def bucket_idx_to_time_str(bucket_idx: int) -> str:
    # For 2023-12 only; used for metadata readability.
    minutes = bucket_idx * 5
    day0 = minutes // (24 * 60)
    minute_of_day = minutes - day0 * 24 * 60
    hh = minute_of_day // 60
    mm = minute_of_day - hh * 60
    day = 1 + day0
    return f"2023-12-{day:02d} {hh:02d}:{mm:02d}:00"


def safe_mean_std(arr: np.ndarray) -> tuple[float, float]:
    x = arr.astype(np.float64)
    mean = float(x.mean()) if x.size > 0 else 0.0
    std = float(x.std()) if x.size > 0 else 0.0
    # Avoid zero std in downstream inverse-normalization
    if std == 0.0:
        std = 1.0
    return mean, std


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for obj in rows:
            f.write(json.dumps(obj, ensure_ascii=False) + "\n")


def main() -> None:
    root = _repo_root()

    parser = argparse.ArgumentParser(description="Build ETC 5-min flow dataset (CP001..CP019 × FXLX) for Time-MoE")
    parser.add_argument(
        "--csv_dir",
        type=str,
        default=str(root / "data" / "expanded"),
        help="Directory containing CSV files (default: <repo>/data/expanded)",
    )
    parser.add_argument(
        "--out_dir",
        type=str,
        default=str(root / "Time-MoE" / "datasets" / "etc_flow_5min"),
        help="Output directory (default: <repo>/Time-MoE/datasets/etc_flow_5min)",
    )
    parser.add_argument(
        "--train_end_date",
        type=str,
        default="2023-12-25",
        help="Train split end date (YYYY-MM-DD), train covers [2023-12-01, train_end_date). Default: 2023-12-25",
    )
    parser.add_argument(
        "--val_context",
        type=int,
        default=512,
        help="Include left context steps before train_end_date in val.jsonl. Default: 512",
    )
    parser.add_argument(
        "--max_rows_per_file",
        type=int,
        default=0,
        help="For quick debug: max rows per csv file (0=all).",
    )
    args = parser.parse_args()

    csv_dir = Path(args.csv_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    month = MONTH_2023_12
    total_steps = month.total_steps

    # Compute train_end_idx: number of 5-min steps from 2023-12-01 00:00 to train_end_date 00:00
    try:
        y_s, m_s, d_s = args.train_end_date.strip().split("-")
        y, m, d = int(y_s), int(m_s), int(d_s)
    except Exception as e:
        raise SystemExit(f"Invalid --train_end_date: {args.train_end_date} ({e})")
    if (y, m) != (month.year, month.month) or not (1 <= d <= month.days + 1):
        raise SystemExit(f"--train_end_date must be within 2023-12, got: {args.train_end_date}")

    # d can be 32 only when pointing to 2024-01-01, but we restrict to month end here
    train_end_day = min(d, month.days + 1)
    train_end_idx = (train_end_day - 1) * month.steps_per_day
    train_end_idx = int(max(0, min(total_steps, train_end_idx)))

    val_context = int(max(0, args.val_context))
    val_start_idx = int(max(0, train_end_idx - val_context))

    # Pre-create 19*2 arrays for stable output ordering
    cps = sorted(set(RAW_KKMC_TO_ID.values()))
    series: dict[tuple[str, str], np.ndarray] = {}
    for cp in cps:
        for fx in VALID_DIRECTIONS:
            series[(cp, fx)] = np.zeros(total_steps, dtype=np.int32)

    # Stream read CSV files
    csv_files = sorted(csv_dir.glob("2023-12-*.csv"))
    if not csv_files:
        raise SystemExit(f"No files matched: {csv_dir.as_posix()}/2023-12-*.csv")

    total_rows = 0
    total_used = 0
    total_mapped = 0
    total_bad_time = 0
    total_bad_dir = 0

    for p in csv_files:
        used_in_file = 0
        rows_in_file = 0
        with p.open("r", encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                rows_in_file += 1
                total_rows += 1
                if args.max_rows_per_file and rows_in_file > args.max_rows_per_file:
                    break

                kkmc = (row.get("KKMC") or "").strip()
                cp = RAW_KKMC_TO_ID.get(kkmc)
                if not cp:
                    continue
                total_mapped += 1

                fx = (row.get("FXLX") or "").strip()
                if fx not in VALID_DIRECTIONS:
                    total_bad_dir += 1
                    continue

                idx = parse_gcsj_to_bucket_idx(row.get("GCSJ") or "", month=month)
                if idx is None:
                    total_bad_time += 1
                    continue

                arr = series.get((cp, fx))
                if arr is None:
                    # Should not happen; keep defensive for unexpected CP codes
                    series[(cp, fx)] = np.zeros(total_steps, dtype=np.int32)
                    arr = series[(cp, fx)]

                arr[idx] += 1
                used_in_file += 1
                total_used += 1

        print(f"[OK] {p.name}: rows={rows_in_file:,}, used={used_in_file:,}")

    # Build jsonl rows
    full_rows: list[dict[str, Any]] = []
    train_rows: list[dict[str, Any]] = []
    val_rows: list[dict[str, Any]] = []
    scales: dict[str, Any] = {
        "freq_min": 5,
        "month": "2023-12",
        "total_steps": total_steps,
        "base_start": "2023-12-01 00:00:00",
        "base_end": "2024-01-01 00:00:00",
        "train_end_date": f"{args.train_end_date} 00:00:00",
        "train_end_idx": train_end_idx,
        "val_context": val_context,
        "val_start_idx": val_start_idx,
        "series": {},
        "stats": {
            "total_rows": total_rows,
            "total_mapped_rows": total_mapped,
            "total_used_rows": total_used,
            "total_bad_time": total_bad_time,
            "total_bad_dir": total_bad_dir,
        },
    }

    for cp in sorted(cps):
        for fx in VALID_DIRECTIONS:
            arr = series[(cp, fx)]
            series_id = f"{cp}#fx={fx}"

            full_seq = arr
            train_seq = arr[:train_end_idx]
            val_seq = arr[val_start_idx:]

            full_mean, full_std = safe_mean_std(full_seq)
            train_mean, train_std = safe_mean_std(train_seq)
            val_mean, val_std = safe_mean_std(val_seq)

            scales["series"][series_id] = {
                "checkpointId": cp,
                "direction": fx,
                "full": {"mean": full_mean, "std": full_std, "length": int(full_seq.size), "start": "2023-12-01 00:00:00"},
                "train": {"mean": train_mean, "std": train_std, "length": int(train_seq.size), "start": "2023-12-01 00:00:00"},
                "val": {
                    "mean": val_mean,
                    "std": val_std,
                    "length": int(val_seq.size),
                    "start": bucket_idx_to_time_str(val_start_idx),
                    "full_start_idx": val_start_idx,
                    "target_start_idx_in_val": int(train_end_idx - val_start_idx),
                },
            }

            full_rows.append(
                {
                    "id": series_id,
                    "checkpointId": cp,
                    "direction": fx,
                    "sequence": [int(x) for x in full_seq.tolist()],
                    "meta": {
                        "freq_min": 5,
                        "start": "2023-12-01 00:00:00",
                        "mean": full_mean,
                        "std": full_std,
                        "split": "full",
                    },
                }
            )
            train_rows.append(
                {
                    "id": series_id,
                    "checkpointId": cp,
                    "direction": fx,
                    "sequence": [int(x) for x in train_seq.tolist()],
                    "meta": {
                        "freq_min": 5,
                        "start": "2023-12-01 00:00:00",
                        "mean": train_mean,
                        "std": train_std,
                        "split": "train",
                    },
                }
            )
            val_rows.append(
                {
                    "id": series_id,
                    "checkpointId": cp,
                    "direction": fx,
                    "sequence": [int(x) for x in val_seq.tolist()],
                    "meta": {
                        "freq_min": 5,
                        "start": bucket_idx_to_time_str(val_start_idx),
                        "mean": val_mean,
                        "std": val_std,
                        "split": "val",
                        "full_start_idx": val_start_idx,
                        "target_start_idx_in_val": int(train_end_idx - val_start_idx),
                    },
                }
            )

    write_jsonl(out_dir / "full.jsonl", full_rows)
    write_jsonl(out_dir / "train.jsonl", train_rows)
    write_jsonl(out_dir / "val.jsonl", val_rows)
    (out_dir / "scales.json").write_text(json.dumps(scales, ensure_ascii=False, indent=2), encoding="utf-8")

    print()
    print("=" * 72)
    print("Dataset build finished.")
    print(f"  out_dir: {out_dir.resolve()}")
    print(f"  series: {len(full_rows)} (checkpoint×direction)")
    print(f"  full steps: {total_steps} ({month.days} days × 288 steps/day)")
    print(f"  train steps: {train_end_idx}")
    print(f"  val steps: {total_steps - val_start_idx} (val_start_idx={val_start_idx}, val_context={val_context})")
    print(f"  rows read: {total_rows:,}, mapped: {total_mapped:,}, used: {total_used:,}")
    print(f"  bad_time: {total_bad_time:,}, bad_dir: {total_bad_dir:,}")
    print("=" * 72)


if __name__ == "__main__":
    main()


