ARG SPARK_VERSION=3.5.4

FROM docker.io/library/python:3.12-slim-bookworm

ARG SPARK_VERSION
ARG MYSQL_CONNECTOR_VERSION=8.4.0

# 使用阿里云镜像源
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources \
  && apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash openjdk-17-jre-headless procps tzdata \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN set -eux; \
  out="spark.tgz"; \
  # 优先尝试清华大学镜像源（国内加速）
  mirror_url="https://mirrors.tuna.tsinghua.edu.cn/apache/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3.tgz"; \
  # 回退到 Apache 官方源
  official_url="https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3.tgz"; \
  echo "尝试从清华镜像下载 Spark ${SPARK_VERSION}..."; \
  if curl -fL --http1.1 --connect-timeout 10 --max-time 300 -o "${out}" "${mirror_url}"; then \
  echo "清华镜像下载成功！"; \
  else \
  echo "清华镜像不可用，回退到 Apache 官方源..."; \
  i=0; \
  while [ "$i" -lt 12 ]; do \
  if curl -fL --http1.1 --connect-timeout 20 --max-time 2400 -C - -o "${out}" "${official_url}"; then \
  break; \
  fi; \
  i=$((i+1)); \
  sleep 2; \
  done; \
  fi; \
  test -s "${out}"; \
  tar -xzf "${out}"; \
  rm "${out}"; \
  ln -s "spark-${SPARK_VERSION}-bin-hadoop3" spark

ENV SPARK_HOME=/opt/spark
ENV PATH="$SPARK_HOME/bin:$SPARK_HOME/sbin:$PATH"

# 注意：本镜像用于 Spark Master/Worker 基础组件，默认不安装 PyTorch/Transformers，
# 避免在 Docker build 时下载超大依赖导致构建过慢。
# 预测推理（Time‑MoE）推荐在本机运行（见仓库 README：python3 scripts/run-forecast-local.py）。

# MySQL JDBC driver for Spark JDBC
RUN set -eux; \
  url="https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"; \
  curl -fL --http1.1 --connect-timeout 20 --max-time 2400 -o "${SPARK_HOME}/jars/mysql-connector-j.jar" "${url}"; \
  test -s "${SPARK_HOME}/jars/mysql-connector-j.jar"

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 7077 8080 8081

ENTRYPOINT ["/entrypoint.sh"]
