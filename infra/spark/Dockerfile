ARG SPARK_VERSION=3.5.4

FROM docker.io/library/python:3.12-slim-bookworm

ARG SPARK_VERSION

# 使用阿里云镜像源
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources \
  && apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash openjdk-17-jre-headless procps \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN set -eux; \
  url="https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3.tgz"; \
  out="spark.tgz"; \
  i=0; \
  while [ "$i" -lt 12 ]; do \
    if curl -fL --http1.1 --connect-timeout 20 --max-time 2400 -C - -o "${out}" "${url}"; then \
      break; \
    fi; \
    i=$((i+1)); \
    sleep 2; \
  done; \
  test -s "${out}"; \
  tar -xzf "${out}"; \
  rm "${out}"; \
  ln -s "spark-${SPARK_VERSION}-bin-hadoop3" spark

ENV SPARK_HOME=/opt/spark
ENV PATH="$SPARK_HOME/bin:$SPARK_HOME/sbin:$PATH"

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 7077 8080 8081

ENTRYPOINT ["/entrypoint.sh"]
