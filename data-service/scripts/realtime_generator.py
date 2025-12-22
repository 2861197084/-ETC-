#!/usr/bin/env python3
"""
实时数据生成器

每秒生成约50条通行记录，发送到 Kafka
使用北京时间（UTC+8）
"""
import os
import sys
import time
import random
import logging
from datetime import datetime, timezone, timedelta

# 北京时区 UTC+8
BEIJING_TZ = timezone(timedelta(hours=8))

# 添加父目录到路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings
from app.kafka_producer import producer

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 卡口配置
CHECKPOINTS = {
    "CP001": ("徐州市睢宁县G104北京-福州K873江苏徐州-G104-苏皖界省际卡口", "睢宁县"),
    "CP002": ("徐州市铜山县G311徐州-西峡K207江苏徐州-G311-苏皖界省际卡口", "铜山区"),
    "CP003": ("徐州市睢宁县S252塔双线K56江苏徐州-S252-苏皖界省际卡口", "睢宁县"),
    "CP004": ("徐州市铜山县G206烟台-汕头K816江苏徐州-G206-苏皖界省际卡口", "铜山区"),
    "CP005": ("徐州市丰县G518518国道K358马楼公路站省际卡口", "丰县"),
    "CP006": ("徐州市丰县G237国道237线K148荣庄卡口省际卡口", "丰县"),
    "CP007": ("徐州市沛县S253郑沛龙线K0江苏徐州-S253-苏鲁界省际卡口", "沛县"),
    "CP008": ("徐州市铜山县G104北京-福州K744江苏徐州-G104-苏鲁界省际卡口", "铜山区"),
    "CP009": ("G3京台高速K731江苏高速五大队江苏徐州-G3-苏鲁界省际卡口", "铜山区"),
    "CP010": ("徐州市邳州市S250宿邳线K1江苏徐州-S250-苏鲁界省际卡口", "邳州市"),
    "CP011": ("徐州市邳州市S251枣睢线K5江苏徐州-S251-苏鲁界省际卡口", "邳州市"),
    "CP012": ("江苏省徐州市新沂市S323连徐线K96瓦窑检查站市际卡口", "新沂市"),
    "CP013": ("徐州市新沂市S323连徐线K10阿湖卡口-323省道连云港交界市际卡口", "新沂市"),
    "CP014": ("徐州市铜山县G310连云港-天水K310江苏徐州-G310-苏皖界省际卡口", "铜山区"),
    "CP015": ("徐州市新沂市S505505省道K10新沂高速西出口-505省道宿迁界市际卡口", "新沂市"),
    "CP016": ("江苏省徐州市睢宁县S325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口", "睢宁县"),
    "CP017": ("徐州市睢宁县S324燕沭睢线K201省道桑庄王马路路口西侧-向东卡口市际卡口", "睢宁县"),
    "CP018": ("徐州市新沂市G235国道235K10江苏徐州-G235-交界市际卡口", "新沂市"),
    "CP019": ("徐州市丰县鹿梁路K19丰县梁寨检查站市际卡口", "丰县"),
}

# 车牌前缀 - 70%本地(苏C)，30%外地
PLATE_PREFIXES_LOCAL = ["苏C"]
PLATE_PREFIXES_FOREIGN = ["苏A", "苏B", "苏D", "苏E", "鲁B", "鲁C", "豫N", "皖A", "皖B"]

DIRECTIONS = ["进城", "出城"]
PLATE_TYPES = ["01", "02", "52"]  # 小型汽车, 大型汽车, 新能源
CAR_BRANDS = ["大众朗逸", "丰田卡罗拉", "本田思域", "别克英朗", "日产轩逸", 
              "现代领动", "福特福克斯", "雪佛兰科鲁兹", "比亚迪秦", "特斯拉Model3"]

# 每秒生成的记录数
RECORDS_PER_SECOND = 50

# 时间格式
TIME_FORMAT = "%Y-%m-%d %H:%M:%S"


def generate_plate() -> tuple[str, bool]:
    """生成随机车牌，返回(车牌号, 是否本地车)"""
    is_local = random.random() < 0.7  # 70% 本地车
    if is_local:
        prefix = random.choice(PLATE_PREFIXES_LOCAL)
    else:
        prefix = random.choice(PLATE_PREFIXES_FOREIGN)
    
    # 生成车牌号
    chars = '0123456789ABCDEFGHJKLMNPQRSTUVWXYZ'
    suffix = ''.join(random.choices(chars, k=5))
    return f"{prefix}{suffix}", is_local


def generate_record(seq: int) -> dict:
    """生成一条通行记录"""
    now = datetime.now(BEIJING_TZ)  # 使用北京时间
    
    # 随机选择卡口（可以根据实际情况调整权重）
    cp_id = random.choice(list(CHECKPOINTS.keys()))
    cp_name, district = CHECKPOINTS[cp_id]
    
    plate, is_local = generate_plate()
    
    # 生成唯一序号
    gcxh = f"R{int(now.timestamp() * 1000) % 100000000}{seq:04d}"
    
    return {
        "gcxh": gcxh,
        "xzqhmc": district,
        "kkmc": cp_name,
        "fxlx": random.choice(DIRECTIONS),
        "gcsj": now.strftime(TIME_FORMAT),
        "hpzl": random.choice(PLATE_TYPES),
        "hp": plate,
        "clppxh": random.choice(CAR_BRANDS),
        "checkpointId": cp_id,
    }


def run_generator():
    """运行实时数据生成器"""
    logger.info("=" * 50)
    logger.info("ETC 实时数据生成器")
    logger.info("=" * 50)
    logger.info(f"Kafka: {settings.kafka_bootstrap_servers}")
    logger.info(f"Topic: {settings.kafka_topic_pass_records}")
    logger.info(f"每秒生成: {RECORDS_PER_SECOND} 条记录")
    
    # 连接 Kafka
    try:
        producer.connect()
    except Exception as e:
        logger.error(f"Kafka 连接失败: {e}")
        return
    
    total_sent = 0
    
    try:
        while True:
            start_time = time.time()
            
            # 生成并发送记录
            for i in range(RECORDS_PER_SECOND):
                record = generate_record(i)
                producer.send(record, key=record.get('hp'))
                total_sent += 1
            
            producer.flush()
            
            # 每分钟输出统计
            if total_sent % (RECORDS_PER_SECOND * 60) == 0:
                logger.info(f"📊 累计发送: {total_sent} 条记录")
            
            # 确保每秒发送一次
            elapsed = time.time() - start_time
            if elapsed < 1.0:
                time.sleep(1.0 - elapsed)
                
    except KeyboardInterrupt:
        logger.info("接收到中断信号")
    finally:
        producer.close()
        logger.info(f"生成器停止，共发送 {total_sent} 条记录")


def main():
    run_generator()


if __name__ == "__main__":
    main()
