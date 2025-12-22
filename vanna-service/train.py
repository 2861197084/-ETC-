"""
训练 Vanna 模型

用法:
    python train.py [--force]
"""
import os
import sys

# 添加 vanna 源码路径
VANNA_SRC = os.path.join(os.path.dirname(__file__), "..", "..", "vanna", "src")
if os.path.exists(VANNA_SRC):
    sys.path.insert(0, VANNA_SRC)

from vanna_instance import get_vanna
from train_data import DDL_STATEMENTS, DOCUMENTATION, EXAMPLE_QA_PAIRS
import config


def train(force: bool = False):
    """训练 Vanna"""
    
    # 检查是否已训练
    marker_file = os.path.join(config.CHROMA_PATH, ".trained")
    if os.path.exists(marker_file) and not force:
        print("✅ 已有训练数据，跳过训练 (使用 --force 强制重新训练)")
        return
    
    print("🚀 开始训练 Vanna...")
    vn = get_vanna()
    
    # 1. 训练 DDL
    print("\n📋 添加表结构 DDL...")
    for i, ddl in enumerate(DDL_STATEMENTS, 1):
        vn.add_ddl(ddl)
        print(f"   [{i}/{len(DDL_STATEMENTS)}] 已添加")
    
    # 2. 训练文档
    print("\n📄 添加业务文档...")
    vn.add_documentation(DOCUMENTATION)
    print("   ✓ 已添加")
    
    # 3. 训练问答对
    print(f"\n💬 添加示例问答对 ({len(EXAMPLE_QA_PAIRS)} 个)...")
    for i, qa in enumerate(EXAMPLE_QA_PAIRS, 1):
        vn.add_question_sql(question=qa["question"], sql=qa["sql"])
        print(f"   [{i}/{len(EXAMPLE_QA_PAIRS)}] {qa['question'][:30]}...")
    
    # 标记完成
    os.makedirs(config.CHROMA_PATH, exist_ok=True)
    with open(marker_file, 'w') as f:
        f.write("trained")
    
    print("\n✅ 训练完成!")
    print(f"   - {len(DDL_STATEMENTS)} 个表结构")
    print(f"   - 1 份业务文档")
    print(f"   - {len(EXAMPLE_QA_PAIRS)} 个示例问答")


def test():
    """测试 Vanna"""
    print("\n🧪 测试查询...")
    vn = get_vanna()
    
    test_questions = [
        "今天的总车流量是多少",
        "查询各卡口的通行量排名",
        "本地车辆和外地车辆的占比"
    ]
    
    for q in test_questions:
        print(f"\n❓ {q}")
        try:
            sql = vn.generate_sql(q)
            print(f"📝 SQL: {sql}")
        except Exception as e:
            print(f"❌ 错误: {e}")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="强制重新训练")
    parser.add_argument("--test", action="store_true", help="测试查询")
    args = parser.parse_args()
    
    if args.test:
        test()
    else:
        train(force=args.force)
        if args.force:
            test()
