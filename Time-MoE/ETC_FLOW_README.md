# Time-MoE 在 ETC 车流量（卡口×方向）预测上的微调说明（本地训练版）

## 论文与引用

- 论文：**Time-MoE: Billion-Scale Time Series Foundation Models with Mixture of Experts**
  - arXiv: `2409.16040`
  - ICLR 2025 Spotlight
  - 入口：见 `Time-MoE/README.md` 的 `Paper Page`

建议引用（来自上游仓库 `Time-MoE/README.md`）：

```bibtex
@misc{shi2024timemoe,
      title={Time-MoE: Billion-Scale Time Series Foundation Models with Mixture of Experts},
      author={Xiaoming Shi and Shiyu Wang and Yuqi Nie and Dianqi Li and Zhou Ye and Qingsong Wen and Ming Jin},
      year={2024},
      eprint={2409.16040},
      archivePrefix={arXiv},
      url={https://arxiv.org/abs/2409.16040},
}
```

## 代码实现关键点（与本项目任务强相关）

### 1) 输入/输出是“连续值序列”，不是离散 token

- 模型是 **decoder-only** 的 time-series foundation model。
- 输入 `input_ids` 是浮点序列（时间序列观测值），而非词表 token。

在 `Time-MoE/time_moe/models/modeling_time_moe.py` 中，`TimeMoeModel.forward` 约定输入 shape 为：
- `input_ids`: `[batch_size, seq_len, input_size]`
- 兼容：如果传入 `[batch_size, seq_len]`，会 **原地 `unsqueeze_(-1)`** 变成 `[batch_size, seq_len, 1]`（默认 `input_size=1`）。

这意味着：我们只要把“车流量”变成一维数值序列（例如 5min 计数）即可直接微调，不需要改模型结构。

### 2) 训练是自回归（Auto-Regressive）回归损失

- 训练数据会被窗口化为：
  - `input_ids`: `seq[:-1]`
  - `labels`: `seq[1:]`
- 模型内部使用回归损失（`HuberLoss(reduction='none')`），支持可选的 `loss_masks` 做 padding mask。

窗口化在 `Time-MoE/time_moe/datasets/time_moe_window_dataset.py`。

### 3) `generate()` 用于连续值预测（多步预测）

`Time-MoE` 覆写了时序生成逻辑（`TSGenerationMixin`），`model.generate(x, max_new_tokens=K)` 会返回
`[batch, context_len + K]`（或 `[batch, context_len + K, 1]` 的中间形态）并取末尾 K 步做预测。

上游 README 给了标准用法（先对输入序列做 mean/std 归一化，再反归一化）。

### 4) 序列长度约束

上游 `README` 明确建议：**`context_length + prediction_length ≤ 4096`**（模型训练时的最大位置长度限制）。
本项目（当前推荐配置）：
- `context_length = 256`
- `prediction_length = 12`（1 小时 = 12×5min）

总长度 268，满足限制。

## 将本任务映射到 Time-MoE

### 目标

- 数据源：`data/expanded/2023-12-*.csv`
- 粒度：**5 分钟**（与项目模拟器的窗口一致）
- 预测：未来 1 小时 **12 个 5min step**
- 维度：**19 个卡口（CP001..CP019）× 方向 `FXLX`（1=进城，2=出城）**

### 标签定义（最关键的口径）

对每个 (checkpointId, direction)：
- 每个 5 分钟桶的值 = **该桶内通行记录条数（count）**
- 预测输出是未来 12 个桶的 count（可在推理阶段 `clip>=0`、并可选 `round` 转整数以便业务解释）

### 数据格式（微调输入）

Time-MoE 微调数据为 `jsonl`：每行一个序列对象，至少包含：

```json
{"sequence": [0, 1, 0, 5, 2, ...]}
```

本项目会额外写入（不影响训练）：`id` 与 `meta(mean/std)`，便于推理反归一化与排查：

```json
{"id":"CP016#fx=2","sequence":[...], "meta":{"mean":0.12,"std":0.45,"start":"2023-12-01 00:00:00","freq_min":5}}
```

## 本地数据构建（CSV -> jsonl）

脚本：`Time-MoE/scripts/build_etc_flow_5min_dataset.py`

从仓库根目录执行（Windows PowerShell 示例）：

```powershell
python Time-MoE/scripts/build_etc_flow_5min_dataset.py `
  --csv_dir data/expanded `
  --out_dir Time-MoE/datasets/etc_flow_5min `
  --train_end_date 2023-12-25 `
  --val_context 512
```

产物（默认输出目录）：

- `Time-MoE/datasets/etc_flow_5min/train.jsonl`：38 条序列（CP001..CP019 × 方向 1/2），长度 6912（12/01–12/24）
- `Time-MoE/datasets/etc_flow_5min/val.jsonl`：长度 2528（包含 val 左侧 512 step 上下文）
- `Time-MoE/datasets/etc_flow_5min/full.jsonl`：长度 8928（整月）
- `Time-MoE/datasets/etc_flow_5min/scales.json`：每条序列 full/train/val 的 mean/std + split 元信息

## 本地微调（8GB 显存建议配置）

### 环境准备

在仓库根目录：

```bash
pip install -r Time-MoE/requirements.txt
```

> 说明：Time-MoE 要求 `transformers==4.40.1`（见 `Time-MoE/requirements.txt` / 上游 README）。
> 若你使用 conda，请先激活环境（例如 `conda activate AI`），避免出现 `ModuleNotFoundError: No module named 'torch'`。

### 训练命令（推荐）

从仓库根目录执行（Windows PowerShell 示例）：

```powershell
python Time-MoE/main.py `
  -d Time-MoE/datasets/etc_flow_5min/train.jsonl `
  -m Maple728/TimeMoE-50M `
  -o Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast `
  --max_length 256 `
  --stride 128 `
  --normalization_method zero `
  --precision fp32 `
  --micro_batch_size 8 `
  --global_batch_size 8 `
  --learning_rate 1e-5 `
  --min_learning_rate 0 `
  --lr_scheduler_type constant `
  --warmup_ratio 0 `
  --num_train_epochs 5 `
  --logging_steps 10 `
  --save_strategy no `
  --attn_implementation eager `
  --dataloader_num_workers 0
```

说明：

- 这是我们在本机实测过的**速度/显存/效果**都比较均衡的一组配置（fp32 + batch=8 + stride=128）。
- `--stride 128` 会减少滑窗样本数（训练更快）。如果你希望更“吃数据”，可以把 stride 改小（例如 64/32），但训练会变慢。
- 如果 `-o Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast` 已存在且你想保留旧模型，建议改一个新的输出目录名避免覆盖。

#### 日志里看到 `loss=0.0 / learning_rate=0.0` 的原因

- **learning_rate 显示 0.0**：你实际 lr 通常是 `1e-5` / `5e-5` 这类小数，Trainer 日志默认保留的小数位会把 `0.0000x` 打印成 `0.0`，这是**显示精度问题**。
- **loss 显示 0.0**：同理。如果你的序列大量为 0（车流稀疏）且做了归一化，Huber loss 可能非常小，也会被格式化显示成 `0.0`。
- **但 `grad_norm=nan` 不是正常现象**：这表示出现了 NaN 梯度（常见于 fp16 数值不稳定/溢出）。建议先用下面的“快速链路验证”配置跑通全流程，再回到 fp16 调参。

### 快速链路验证（强烈推荐先做，2~5 分钟内跑通）

目标：只验证 **数据→训练→保存→推理→评估** 链路正确，不追求效果。

```powershell
python Time-MoE/main.py `
  -d Time-MoE/datasets/etc_flow_5min/train.jsonl `
  -m Maple728/TimeMoE-50M `
  -o Time-MoE/logs/etc_flow_5min_smoke `
  --max_length 256 `
  --stride 16 `
  --normalization_method zero `
  --precision fp32 `
  --micro_batch_size 1 `
  --global_batch_size 4 `
  --learning_rate 1e-5 `
  --lr_scheduler_type constant `
  --warmup_ratio 0 `
  --train_steps 50 `
  --logging_steps 1 `
  --save_strategy no `
  --attn_implementation eager `
  --dataloader_num_workers 0
```

说明：

- `--precision fp32`：优先避免 fp16 引起的 `grad_norm=nan`（链路验证更稳）
- `--max_length 256`、`--stride 16`：显著加快训练
- `--dataloader_num_workers 0`：Windows 下避免 dataloader 多进程额外开销/警告刷屏

显存不够时的降配顺序（从上到下依次尝试）：

- 把 `--micro_batch_size` 从 8 降到 4 / 2 / 1（并同步把 `--global_batch_size` 调到相同值，避免梯度累积拖慢）
- 把 `--stride` 从 128 提到 256（更快、但更少训练窗口）
- 把 `--max_length` 从 256 降到 192 / 128（更快、显存更省，但历史上下文更短）

### 输出目录

`Time-MoE/main.py` 会把训练产物写到 `-o/--output_path` 指定目录（默认会包含 transformers Trainer 的 checkpoint/最终模型）。

## 离线评估（val.jsonl 滑窗评估）

脚本：`Time-MoE/scripts/eval_etc_flow_5min.py`

从仓库根目录执行（Windows PowerShell 示例）：

```powershell
python Time-MoE/scripts/eval_etc_flow_5min.py `
  --model_path Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast `
  --val_path Time-MoE/datasets/etc_flow_5min/val.jsonl `
  --scales_path Time-MoE/datasets/etc_flow_5min/scales.json `
  --context_length 256 `
  --prediction_length 12 `
  --eval_stride 12 `
  --batch_size 16 `
  --norm context `
  --out_csv Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast/val_metrics.csv
```

如果你用的是上面的 smoke 配置（`max_length=256`），对应把 `--context_length 256` 即可：

```powershell
python Time-MoE/scripts/eval_etc_flow_5min.py `
  --model_path Time-MoE/logs/etc_flow_5min_smoke `
  --val_path Time-MoE/datasets/etc_flow_5min/val.jsonl `
  --scales_path Time-MoE/datasets/etc_flow_5min/scales.json `
  --context_length 256 `
  --prediction_length 12 `
  --eval_stride 12 `
  --batch_size 16 `
  --norm context
```

说明：

- `--eval_stride 12`：每 1 小时评估一次（12×5min），更快；想更细可改为 1
- `--norm context`：按每个窗口的上下文均值/方差做归一化（与上游 README 的推理示例一致）
- 输出会打印：全局 MAE/MSE/RMSE、每个 horizon 的 MAE、Top-K 最差卡口×方向

### 当前结果（供参考）

在同一套评估设置下（`context_length=256`，`prediction_length=12`，`eval_stride=12`，`norm=context`）：

- 预训练 `Maple728/TimeMoE-50M`：MAE **97.8398**，RMSE **228.2364**
- 微调 **1 epoch**：MAE **93.0796**，RMSE **218.3327**
- 微调 **5 epoch（推荐）**：MAE **91.8324**，RMSE **218.6842**（MAE 当前最好）
- 微调 **20 epoch**：MAE **102.6118**，RMSE **229.1742**（出现退化，疑似过拟合/漂移）

> 备注：不同随机种子/stride/学习率会影响结果；建议以你最终选用的训练配置跑一遍评估脚本确认。

## 本地推理（输出未来 12×5min）

脚本：`Time-MoE/scripts/predict_etc_flow_5min.py`

### 例 1：从序列末端开始预测（默认）

```powershell
python Time-MoE/scripts/predict_etc_flow_5min.py `
  --checkpoint_id CP016 `
  --direction 2 `
  --model_path Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast `
  --data_path Time-MoE/datasets/etc_flow_5min/full.jsonl `
  --scales_path Time-MoE/datasets/etc_flow_5min/scales.json `
  --context_length 256 `
  --prediction_length 12 `
  --norm context `
  --clip-nonneg `
  --round
```

### 例 2：指定预测起点（例如 12/25 00:00）

```powershell
python Time-MoE/scripts/predict_etc_flow_5min.py `
  --checkpoint_id CP016 `
  --direction 2 `
  --at_time "2023-12-25 00:00:00" `
  --model_path Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast `
  --data_path Time-MoE/datasets/etc_flow_5min/full.jsonl `
  --context_length 256 `
  --prediction_length 12 `
  --norm context `
  --clip-nonneg `
  --round
```

### 例 3：输出 JSON（便于后续接入接口时直接复用）

```powershell
python Time-MoE/scripts/predict_etc_flow_5min.py `
  --checkpoint_id CP016 `
  --direction 2 `
  --at_time "2023-12-25 00:00:00" `
  --model_path Time-MoE/logs/etc_flow_5min_epoch5_fp32_fast `
  --data_path Time-MoE/datasets/etc_flow_5min/full.jsonl `
  --json
```

## 常见问题（Windows/8GB GPU）

- **flash-attn 安装失败**：Windows 上很常见。训练命令里 `--attn_implementation auto` 会自动回退到 eager，不影响功能，只是速度慢一些。
- **显存 OOM**：
  - 先降 `--micro_batch_size 8 -> 4 -> 2 -> 1`（并同步把 `--global_batch_size` 设成相同值）
  - 再降 `--max_length 256 -> 192 -> 128`
  - 或把 `--stride 128 -> 256`（训练更快、样本更少）
  - 仍不够可尝试开启 `--gradient_checkpointing`（会变慢，但更省显存）
- **数据构建太慢**：
  - 可先用 `--max_rows_per_file` 做抽样跑通流程，再去掉该参数跑全量
- **输出为负数**：回归模型可能产生负值；推理脚本默认 `--clip-nonneg`，并可 `--round` 转成整数车流量。



