"""
Kafka 生产者 - 将通行记录发送到 Kafka
"""
import json
import logging
from typing import Optional
from kafka import KafkaProducer
from kafka.errors import KafkaError

from app.config import settings

logger = logging.getLogger(__name__)


class PassRecordProducer:
    """通行记录 Kafka 生产者"""

    def __init__(self):
        self._producer: Optional[KafkaProducer] = None

    def connect(self):
        """连接 Kafka"""
        try:
            self._producer = KafkaProducer(
                bootstrap_servers=settings.kafka_bootstrap_servers,
                value_serializer=lambda v: json.dumps(v, ensure_ascii=False, default=str).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
                retries=3,
                max_in_flight_requests_per_connection=1
            )
            logger.info(f"Kafka 连接成功: {settings.kafka_bootstrap_servers}")
        except KafkaError as e:
            logger.error(f"Kafka 连接失败: {e}")
            raise

    def send(self, record: dict, key: str = None):
        """发送记录到 Kafka"""
        if not self._producer:
            self.connect()

        try:
            future = self._producer.send(
                settings.kafka_topic_pass_records,
                value=record,
                key=key
            )
            # 不等待确认，异步发送
            return future
        except KafkaError as e:
            logger.error(f"发送消息失败: {e}")
            raise

    def flush(self):
        """刷新缓冲区"""
        if self._producer:
            self._producer.flush()

    def close(self):
        """关闭连接"""
        if self._producer:
            self._producer.close()
            logger.info("Kafka 连接已关闭")


# 单例
producer = PassRecordProducer()
