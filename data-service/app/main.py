"""
ETC 数据服务入口
"""
import asyncio
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

logger = logging.getLogger(__name__)


async def main():
    logger.info("ETC 数据服务启动")
    logger.info("使用 scripts/import_to_hbase.py 导入历史数据到 HBase (2023-12)")
    logger.info("使用 scripts/realtime_simulator.py 模拟实时数据")
    
    # 保持运行
    while True:
        await asyncio.sleep(60)


if __name__ == "__main__":
    asyncio.run(main())
