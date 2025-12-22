#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ETC 车流量预测（Time-MoE）Spark 作业：

- 从 MySQL（ShardingSphere Proxy）读取 pass_record，在最近 256 个 5min bucket 上聚合 count（按 checkpoint_id × fxlx）
- 用 Time-MoE 模型预测未来 12 个 5min 点
- 写回 MySQL 表 checkpoint_flow_forecast_5m
- 处理前端触发的 forecast_request（pending -> done/failed）

设计说明：
- 预测每分钟触发：作业支持 --loop_seconds 以循环方式常驻运行，每分钟检查 pending 请求，若有则产出一轮预测并落库。
- 预测起点 start_time 取“下一段 5min 边界”（window_end）。最后一个上下文桶会使用当前桶的部分数据做估计（按时间占比缩放，最小按 1 分钟占比）。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import numpy as np


def _dt_fmt(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def align_floor_5min(t: datetime) -> datetime:
    t0 = t.replace(second=0, microsecond=0)
    aligned_min = (t0.minute // 5) * 5
    return t0.replace(minute=aligned_min)


def align_ceil_5min(t: datetime) -> datetime:
    floor = align_floor_5min(t)
    if floor == t.replace(second=0, microsecond=0):
        # already aligned to minute; still need to ensure it is 5-min aligned
        if t.minute % 5 == 0 and t.second == 0:
            return floor
    return floor + timedelta(minutes=5)


def _add_time_moe_to_syspath() -> None:
    """
    约定容器内挂载：
      /workspace/Time-MoE/time_moe/...
    或者运行时从 repo 根目录执行。
    """
    # Prefer /workspace/Time-MoE (docker mount)
    candidates = []
    try:
        candidates.append(Path("/workspace/Time-MoE"))
    except Exception:
        pass
    # Fallback: repo-relative (spark-jobs/..)
    candidates.append(Path(__file__).resolve().parents[1] / "Time-MoE")

    for p in candidates:
        if (p / "time_moe").exists():
            if str(p) not in sys.path:
                sys.path.insert(0, str(p))
            return

    raise RuntimeError("Cannot locate Time-MoE source. Please mount repo/Time-MoE into the container.")


def load_timemoe_model(model_dir: str, device: str = "cpu", dtype: str = "fp32"):
    import torch

    _add_time_moe_to_syspath()
    from time_moe.models.modeling_time_moe import TimeMoeForPrediction

    if dtype == "fp16":
        torch_dtype: Any = torch.float16
    elif dtype == "bf16":
        torch_dtype = torch.bfloat16
    else:
        torch_dtype = torch.float32

    model = TimeMoeForPrediction.from_pretrained(model_dir, device_map=device, torch_dtype=torch_dtype)
    model.eval()
    return model


@dataclass
class MySqlConn:
    host: str
    port: int
    user: str
    password: str
    database: str


def mysql_connect(conn: MySqlConn):
    import pymysql

    return pymysql.connect(
        host=conn.host,
        port=conn.port,
        user=conn.user,
        password=conn.password,
        database=conn.database,
        charset="utf8mb4",
        autocommit=True,
    )


def ensure_forecast_tables(conn: MySqlConn) -> None:
    """
    保障预测相关表存在。

    说明：这两个表在 docker 初始化 SQL 里会创建，但如果你的 MySQL volume 早于该变更已存在，
    init SQL 不会再次执行，导致本机预测轮询启动时报 “table does not exist”。
    """
    ddl_forecast = """
    CREATE TABLE IF NOT EXISTS checkpoint_flow_forecast_5m (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        checkpoint_id VARCHAR(64) NOT NULL COMMENT '卡口ID（CP001..CP019）',
        fxlx VARCHAR(32) NOT NULL COMMENT '方向类型 FXLX（1=进城，2=出城）',
        start_time DATETIME NOT NULL COMMENT '预测起点（对齐 5min，预测第 1 个点的时间）',
        freq_min INT NOT NULL DEFAULT 5 COMMENT '时间粒度（分钟），固定 5',
        context_length INT NOT NULL DEFAULT 256 COMMENT '上下文长度（5min 点数）',
        prediction_length INT NOT NULL DEFAULT 12 COMMENT '预测长度（未来点数）',
        values_json JSON NOT NULL COMMENT '预测值数组（长度=prediction_length）',
        model_version VARCHAR(64) NOT NULL DEFAULT 'timemoe_etc_flow_v1' COMMENT '模型版本标识',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uk_series_time_model (checkpoint_id, fxlx, start_time, model_version),
        KEY idx_start_time (start_time),
        KEY idx_checkpoint_time (checkpoint_id, start_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡口×方向 5min 流量预测（未来 12 点）';
    """

    ddl_request = """
    CREATE TABLE IF NOT EXISTS forecast_request (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        checkpoint_id VARCHAR(64) NOT NULL COMMENT '卡口ID（CP001..CP019）',
        fxlx VARCHAR(32) NOT NULL COMMENT '方向类型 FXLX（1=进城，2=出城）',
        as_of_time DATETIME NOT NULL COMMENT '请求发起时间（用于标记“新一轮预测”）',
        status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/done/failed',
        result_start_time DATETIME NULL COMMENT '预测起点（done 时写入，便于查询结果）',
        model_version VARCHAR(64) NOT NULL DEFAULT 'timemoe_etc_flow_v1' COMMENT '模型版本标识',
        err_msg VARCHAR(512) NULL COMMENT '失败原因（failed 时）',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        KEY idx_status_created (status, created_at),
        KEY idx_series_created (checkpoint_id, fxlx, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测刷新请求队列（前端每分钟触发）';
    """

    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            cur.execute(ddl_forecast)
            cur.execute(ddl_request)
    finally:
        db.close()


def fetch_checkpoints(conn: MySqlConn) -> list[str]:
    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            cur.execute("SELECT code FROM checkpoint ORDER BY code")
            return [str(r[0]) for r in cur.fetchall() if r and r[0]]
    finally:
        db.close()


def fetch_pending_requests(conn: MySqlConn, model_version: str, limit: int = 200) -> list[dict[str, Any]]:
    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            cur.execute(
                """
                SELECT id, checkpoint_id, fxlx, as_of_time, created_at
                FROM forecast_request
                WHERE status='pending' AND model_version=%s
                ORDER BY created_at ASC
                LIMIT %s
                """,
                (model_version, int(limit)),
            )
            cols = [d[0] for d in cur.description]
            return [dict(zip(cols, row)) for row in cur.fetchall()]
    finally:
        db.close()


def mark_requests_done(conn: MySqlConn, ids: list[int], result_start_time: datetime) -> None:
    if not ids:
        return
    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            placeholders = ",".join(["%s"] * len(ids))
            cur.execute(
                f"""
                UPDATE forecast_request
                SET status='done', result_start_time=%s, err_msg=NULL, updated_at=NOW()
                WHERE id IN ({placeholders})
                """,
                (result_start_time, *ids),
            )
    finally:
        db.close()


def mark_requests_failed(conn: MySqlConn, ids: list[int], err_msg: str) -> None:
    if not ids:
        return
    msg = (err_msg or "")[:512]
    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            placeholders = ",".join(["%s"] * len(ids))
            cur.execute(
                f"""
                UPDATE forecast_request
                SET status='failed', err_msg=%s, updated_at=NOW()
                WHERE id IN ({placeholders})
                """,
                (msg, *ids),
            )
    finally:
        db.close()


def upsert_forecasts(
    conn: MySqlConn,
    rows: list[tuple[str, str, datetime, int, int, int, str, str]],
) -> None:
    """
    rows: (checkpoint_id, fxlx, start_time, freq_min, context_length, prediction_length, values_json, model_version)
    """
    if not rows:
        return
    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            cur.executemany(
                """
                INSERT INTO checkpoint_flow_forecast_5m
                  (checkpoint_id, fxlx, start_time, freq_min, context_length, prediction_length, values_json, model_version, created_at, updated_at)
                VALUES
                  (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
                AS new
                ON DUPLICATE KEY UPDATE
                  values_json = new.values_json,
                  context_length = new.context_length,
                  prediction_length = new.prediction_length,
                  updated_at = NOW()
                """,
                rows,
            )
    finally:
        db.close()


def build_context_series(
    spark,
    jdbc_url: str,
    jdbc_user: str,
    jdbc_password: str,
    begin_time: datetime,
    as_of_time: datetime,
    context_length: int,
    checkpoints: list[str],
) -> dict[tuple[str, str], np.ndarray]:
    """
    返回：{ (checkpoint_id, fxlx) -> np.ndarray(shape=[context_length], dtype=float32) }
    """
    # Expected bucket starts
    bucket_starts = [begin_time + timedelta(minutes=5 * i) for i in range(context_length)]
    bucket_index = {dt: i for i, dt in enumerate(bucket_starts)}

    # init all series
    series: dict[tuple[str, str], np.ndarray] = {}
    for cp in checkpoints:
        for fx in ("1", "2"):
            series[(cp, fx)] = np.zeros(context_length, dtype=np.float32)

    # Query aggregated counts from MySQL
    begin_str = _dt_fmt(begin_time)
    as_of_str = _dt_fmt(as_of_time)

    # Narrow by checkpoint_id to reduce scan (19 ids)
    cp_in = ",".join([f"'{cp}'" for cp in checkpoints]) or "''"
    query = f"""
      (SELECT
         checkpoint_id,
         CASE
           WHEN fxlx = '进城' THEN '1'
           WHEN fxlx = '出城' THEN '2'
           ELSE fxlx
         END AS fxlx_mapped,
         FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(gcsj)/300)*300) AS bucket_start,
         COUNT(*) AS flow
       FROM pass_record
       WHERE gcsj >= '{begin_str}' AND gcsj < '{as_of_str}'
         AND checkpoint_id IN ({cp_in})
         AND fxlx IN ('1','2','进城','出城')
       GROUP BY checkpoint_id, fxlx_mapped, bucket_start
      ) AS t
    """

    df = (
        spark.read.format("jdbc")
        .option("url", jdbc_url)
        .option("dbtable", query)
        .option("user", jdbc_user)
        .option("password", jdbc_password)
        .option("driver", "com.mysql.cj.jdbc.Driver")
        .load()
    )

    rows = df.collect()
    for r in rows:
        cp = str(r["checkpoint_id"])
        fx = str(r["fxlx_mapped"])
        b = r["bucket_start"]
        # PySpark may return datetime or string depending on driver
        if isinstance(b, str):
            bucket_dt = datetime.strptime(b, "%Y-%m-%d %H:%M:%S")
        else:
            bucket_dt = b
        idx = bucket_index.get(bucket_dt)
        if idx is None:
            continue
        key = (cp, fx)
        if key not in series:
            continue
        series[key][idx] = float(r["flow"])

    # Scale the last bucket (current partial 5-min window) to full-window estimate.
    window_start = bucket_starts[-1]
    elapsed = max(0.0, (as_of_time - window_start).total_seconds())
    # Avoid extreme scaling when the window刚开始：最小按 1 分钟占比（=0.2）
    fraction = min(max(elapsed / 300.0, 0.2), 1.0)
    if fraction < 1.0:
        for k in series:
            series[k][-1] = float(series[k][-1] / fraction)

    return series


def build_context_series_mysql(
    conn: MySqlConn,
    begin_time: datetime,
    as_of_time: datetime,
    context_length: int,
    checkpoints: list[str],
) -> dict[tuple[str, str], np.ndarray]:
    """
    不依赖 Spark 的版本：直接用 PyMySQL 从 MySQL 聚合 5min bucket。
    返回：{ (checkpoint_id, fxlx) -> np.ndarray(shape=[context_length], dtype=float32) }
    """
    # Expected bucket starts
    bucket_starts = [begin_time + timedelta(minutes=5 * i) for i in range(context_length)]
    bucket_index = {dt: i for i, dt in enumerate(bucket_starts)}

    # init all series
    series: dict[tuple[str, str], np.ndarray] = {}
    for cp in checkpoints:
        for fx in ("1", "2"):
            series[(cp, fx)] = np.zeros(context_length, dtype=np.float32)

    begin_str = _dt_fmt(begin_time)
    as_of_str = _dt_fmt(as_of_time)

    if not checkpoints:
        return series

    placeholders = ",".join(["%s"] * len(checkpoints))
    sql = f"""
        SELECT
            checkpoint_id,
            CASE
              WHEN fxlx = '进城' THEN '1'
              WHEN fxlx = '出城' THEN '2'
              ELSE fxlx
            END AS fxlx_mapped,
            FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(gcsj)/300)*300) AS bucket_start,
            COUNT(*) AS flow
        FROM pass_record
        WHERE gcsj >= %s AND gcsj < %s
          AND checkpoint_id IN ({placeholders})
          AND fxlx IN ('1','2','进城','出城')
        GROUP BY checkpoint_id, fxlx_mapped, bucket_start
    """

    db = mysql_connect(conn)
    try:
        with db.cursor() as cur:
            cur.execute(sql, (begin_str, as_of_str, *checkpoints))
            rows = cur.fetchall()
    finally:
        db.close()

    # rows: tuple layout aligned with SELECT
    for r in rows:
        if not r or len(r) < 4:
            continue
        cp = str(r[0])
        fx = str(r[1])
        b = r[2]
        flow = r[3]

        if isinstance(b, str):
            bucket_dt = datetime.strptime(b, "%Y-%m-%d %H:%M:%S")
        else:
            bucket_dt = b

        idx = bucket_index.get(bucket_dt)
        if idx is None:
            continue

        key = (cp, fx)
        if key not in series:
            continue
        series[key][idx] = float(flow)

    # Scale the last bucket (current partial 5-min window) to full-window estimate.
    window_start = bucket_starts[-1]
    elapsed = max(0.0, (as_of_time - window_start).total_seconds())
    # Avoid extreme scaling when the window刚开始：最小按 1 分钟占比（=0.2）
    fraction = min(max(elapsed / 300.0, 0.2), 1.0)
    if fraction < 1.0:
        for k in series:
            series[k][-1] = float(series[k][-1] / fraction)

    return series


def predict_12_steps(model, ctx: np.ndarray, pred_len: int, clip_nonneg: bool = True, round_int: bool = True) -> list[float]:
    import torch

    x = ctx.astype(np.float32)
    mean = float(x.mean(dtype=np.float64))
    std = float(x.std(dtype=np.float64))
    if std == 0.0:
        std = 1.0
    norm_ctx = (x - mean) / std

    device = next(model.parameters()).device
    t = torch.from_numpy(norm_ctx[None, :]).to(device).to(model.dtype)
    with torch.no_grad():
        out = model.generate(t, max_new_tokens=int(pred_len))
    if out.ndim == 3:
        out = out.squeeze(-1)
    pred_norm = out[:, -pred_len:].to(torch.float32).cpu().numpy()[0]
    preds = pred_norm * std + mean

    if clip_nonneg:
        preds = np.maximum(preds, 0.0)
    if round_int:
        preds = np.rint(preds)
    return [float(v) for v in preds.tolist()]


def main() -> None:
    parser = argparse.ArgumentParser("ETC Time-MoE forecast Spark job (5min x 12)")
    parser.add_argument("--mysql_host", type=str, default=os.getenv("MYSQL_HOST", "shardingsphere"))
    parser.add_argument("--mysql_port", type=int, default=int(os.getenv("MYSQL_PORT", "3307")))
    parser.add_argument("--mysql_user", type=str, default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql_password", type=str, default=os.getenv("MYSQL_PASSWORD", "root"))
    parser.add_argument("--mysql_db", type=str, default=os.getenv("MYSQL_DB", "etc"))
    parser.add_argument("--spark_master", type=str, default=os.getenv("SPARK_MASTER_URL", "spark://spark-master:7077"))

    parser.add_argument("--model_dir", type=str, default=os.getenv("MODEL_DIR", "/workspace/model/etc_flow_5min_epoch5"))
    parser.add_argument("--model_version", type=str, default=os.getenv("MODEL_VERSION", "timemoe_etc_flow_v1"))
    parser.add_argument(
        "--context_engine",
        type=str,
        default=os.getenv("CONTEXT_ENGINE", "spark"),
        choices=["spark", "mysql"],
        help="上下文构建方式：spark=Spark JDBC 聚合；mysql=直接 MySQL 聚合（本机 GPU 跑推理时推荐）",
    )

    parser.add_argument("--context_length", type=int, default=256)
    parser.add_argument("--prediction_length", type=int, default=12)
    parser.add_argument("--freq_min", type=int, default=5)

    parser.add_argument("--loop_seconds", type=int, default=int(os.getenv("LOOP_SECONDS", "0")))
    parser.add_argument("--max_pending", type=int, default=200)
    parser.add_argument("--always_run", action="store_true", help="No pending requests: still run one round per loop.")

    parser.add_argument(
        "--device",
        type=str,
        default=os.getenv("TORCH_DEVICE", "auto"),
        choices=["auto", "cuda", "cpu"],
        help="模型运行设备：auto/cuda/cpu（默认 auto；有 GPU 会优先用 GPU）",
    )
    parser.add_argument("--dtype", type=str, default=os.getenv("TORCH_DTYPE", "fp32"), choices=["fp16", "bf16", "fp32"])
    args = parser.parse_args()

    # Resolve model dir fallback (dev workspace)
    model_dir = Path(args.model_dir)
    if not model_dir.exists():
        fallbacks = [
            # docker mount
            Path("/workspace/model/etc_flow_5min_epoch5"),
            Path("/workspace/model/etc_flow_5min_epoch5_fp32_fast"),
            Path("/workspace/Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast"),
            # repo-local
            Path(__file__).resolve().parents[1] / "model" / "etc_flow_5min_epoch5",
            Path(__file__).resolve().parents[1] / "model" / "etc_flow_5min_epoch5_fp32_fast",
        ]
        for fb in fallbacks:
            if fb.exists():
                model_dir = fb
                break
        else:
            raise SystemExit(
                f"Model dir not found: {args.model_dir}. Please place model under repo/model/ or mount into /workspace/model."
            )

    conn = MySqlConn(
        host=args.mysql_host,
        port=int(args.mysql_port),
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_db,
    )

    spark = None
    jdbc_url = None
    if args.context_engine == "spark":
        # Spark session
        from pyspark.sql import SparkSession

        spark = (
            SparkSession.builder.appName("ETCFlowForecastJob")
            .master(args.spark_master)
            .config("spark.sql.session.timeZone", "Asia/Shanghai")
            .getOrCreate()
        )
        spark.sparkContext.setLogLevel("WARN")
        jdbc_url = f"jdbc:mysql://{args.mysql_host}:{args.mysql_port}/{args.mysql_db}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"

    print("=" * 72)
    print("ETC Flow Forecast Job (Time-MoE)")
    print(f"  context_engine={args.context_engine}")
    if args.context_engine == "spark":
        print(f"  spark_master={args.spark_master}")
    print(f"  mysql={args.mysql_host}:{args.mysql_port}/{args.mysql_db}")
    print(f"  model_dir={model_dir}")
    print(f"  model_version={args.model_version}")
    print(f"  context_length={args.context_length}, prediction_length={args.prediction_length}, freq_min={args.freq_min}")
    print(f"  loop_seconds={args.loop_seconds}, always_run={args.always_run}")
    print("=" * 72)

    model = load_timemoe_model(str(model_dir), device=args.device, dtype=args.dtype)

    # Ensure forecast tables exist (for existing docker volumes)
    ensure_forecast_tables(conn)

    checkpoints = fetch_checkpoints(conn)
    if not checkpoints:
        raise SystemExit("No checkpoints found in MySQL table checkpoint.")

    def run_once() -> None:
        pending = fetch_pending_requests(conn, model_version=args.model_version, limit=args.max_pending)
        if not pending and not args.always_run:
            return

        now = datetime.now()
        window_start = align_floor_5min(now)
        window_end = window_start + timedelta(minutes=5)
        start_time = window_end

        context_len = int(args.context_length)
        pred_len = int(args.prediction_length)
        begin_time = start_time - timedelta(minutes=int(args.freq_min) * context_len)

        # build context series for all cp×fx
        if args.context_engine == "spark":
            assert spark is not None and jdbc_url is not None
            series = build_context_series(
                spark=spark,
                jdbc_url=jdbc_url,
                jdbc_user=args.mysql_user,
                jdbc_password=args.mysql_password,
                begin_time=begin_time,
                as_of_time=now,
                context_length=context_len,
                checkpoints=checkpoints,
            )
        else:
            series = build_context_series_mysql(
                conn=conn,
                begin_time=begin_time,
                as_of_time=now,
                context_length=context_len,
                checkpoints=checkpoints,
            )

        out_rows: list[tuple[str, str, datetime, int, int, int, str, str]] = []
        for (cp, fx), ctx in series.items():
            values = predict_12_steps(model, ctx, pred_len=pred_len, clip_nonneg=True, round_int=True)
            out_rows.append(
                (
                    cp,
                    fx,
                    start_time,
                    int(args.freq_min),
                    context_len,
                    pred_len,
                    json.dumps(values, ensure_ascii=False),
                    args.model_version,
                )
            )

        upsert_forecasts(conn, out_rows)

        if pending:
            ids = [int(r["id"]) for r in pending if r.get("id") is not None]
            mark_requests_done(conn, ids, result_start_time=start_time)

        print(
            f"[OK] forecast written: start_time={_dt_fmt(start_time)}, series={len(out_rows)}, pending_handled={len(pending)}"
        )

    try:
        if args.loop_seconds and args.loop_seconds > 0:
            while True:
                try:
                    run_once()
                except Exception as e:
                    # Fail all pending requests in this iteration
                    pending = fetch_pending_requests(conn, model_version=args.model_version, limit=args.max_pending)
                    ids = [int(r["id"]) for r in pending if r.get("id") is not None]
                    mark_requests_failed(conn, ids, err_msg=str(e))
                    print(f"[ERR] run_once failed: {e}")
                time.sleep(int(args.loop_seconds))
        else:
            run_once()
    finally:
        try:
            if spark is not None:
                spark.stop()
        except Exception:
            pass


if __name__ == "__main__":
    main()


