"""
ETC 数据服务 - 配置
"""
import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # MySQL 配置
    mysql_host: str = os.getenv("MYSQL_HOST", "localhost")
    mysql_port: int = int(os.getenv("MYSQL_PORT", "3307"))
    mysql_user: str = os.getenv("MYSQL_USER", "root")
    mysql_password: str = os.getenv("MYSQL_PASSWORD", "root")
    mysql_database: str = os.getenv("MYSQL_DATABASE", "etc")

    # Redis 配置
    redis_host: str = os.getenv("REDIS_HOST", "localhost")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))

    # Kafka 配置
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092")
    kafka_topic_pass_records: str = "etc-pass-records"
    kafka_topic_clone_plate: str = "etc.alerts.clone_plate"

    # 数据目录
    data_dir: str = os.getenv("DATA_DIR", "../data/expanded")

    class Config:
        env_file = ".env"


settings = Settings()
