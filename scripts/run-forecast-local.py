#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
跨平台：本机运行 Time‑MoE 预测轮询（消费 forecast_request → 写 checkpoint_flow_forecast_5m）

用法示例：
  python scripts/run-forecast-local.py --python E:/anaconda3/envs/AI/python.exe

说明：
  - 该脚本本身不做推理逻辑，只负责找到模型目录并启动 spark-jobs/etc_flow_forecast_job.py
  - 推理依赖（torch/transformers）安装在本机 Python 环境里（GPU）
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


def find_repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def resolve_model_dir(repo_root: Path, model_dir: str | None) -> Path:
    if model_dir:
        p = Path(model_dir).expanduser().resolve()
        if p.exists():
            return p
        raise SystemExit(f"ModelDir 不存在：{p}")

    candidates = [
        repo_root / "model" / "etc_flow_5min_epoch5",
        repo_root / "model" / "etc_flow_5min_epoch5_fp32_fast",
    ]
    for c in candidates:
        if c.exists():
            return c
    raise SystemExit("未找到模型目录：请把模型放到 model/etc_flow_5min_epoch5(_fp32_fast)，或用 --model_dir 指定")


def main() -> int:
    parser = argparse.ArgumentParser("Run ETC forecast loop locally (cross-platform)")
    parser.add_argument("--python", dest="python_bin", default=os.getenv("PYTHON", ""), help="Python 可执行文件路径（默认用当前 python）")

    parser.add_argument("--mysql_host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--mysql_port", type=int, default=int(os.getenv("MYSQL_PORT", "3307")))
    parser.add_argument("--mysql_user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql_password", default=os.getenv("MYSQL_PASSWORD", os.getenv("MYSQL_ROOT_PASSWORD", "root")))
    parser.add_argument("--mysql_db", default=os.getenv("MYSQL_DB", "etc"))

    parser.add_argument("--loop_seconds", type=int, default=int(os.getenv("LOOP_SECONDS", "10")))
    parser.add_argument("--device", default=os.getenv("TORCH_DEVICE", "auto"))
    parser.add_argument("--dtype", default=os.getenv("TORCH_DTYPE", "fp32"))
    parser.add_argument("--model_dir", default=os.getenv("MODEL_DIR", ""), help="模型目录（不填则自动从 model/ 下寻找）")

    args = parser.parse_args()

    repo_root = find_repo_root()
    job = repo_root / "spark-jobs" / "etc_flow_forecast_job.py"
    if not job.exists():
        raise SystemExit(f"找不到预测作业脚本：{job}")

    model_dir = resolve_model_dir(repo_root, args.model_dir or None)

    python_bin = args.python_bin.strip() or sys.executable

    cmd = [
        python_bin,
        str(job),
        "--context_engine",
        "mysql",
        "--mysql_host",
        args.mysql_host,
        "--mysql_port",
        str(args.mysql_port),
        "--mysql_user",
        args.mysql_user,
        "--mysql_password",
        args.mysql_password,
        "--mysql_db",
        args.mysql_db,
        "--model_dir",
        str(model_dir),
        "--loop_seconds",
        str(args.loop_seconds),
        "--device",
        args.device,
        "--dtype",
        args.dtype,
    ]

    print("=" * 60)
    print("ETC 预测轮询（本机运行，跨平台）")
    print(f"  python        = {python_bin}")
    print(f"  mysql         = {args.mysql_host}:{args.mysql_port}/{args.mysql_db}")
    print(f"  model_dir     = {model_dir}")
    print(f"  loop_seconds  = {args.loop_seconds}")
    print(f"  device/dtype  = {args.device} / {args.dtype}")
    print("  context_engine= mysql")
    print("说明：前端“预测分析页”每分钟会写 forecast_request，本进程负责消费并写回预测结果。")
    print("=" * 60)

    return subprocess.call(cmd, cwd=str(repo_root))


if __name__ == "__main__":
    raise SystemExit(main())


