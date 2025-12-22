ARG HBASE_VERSION=2.6.1

FROM docker.io/library/eclipse-temurin:17-jre-jammy

ARG HBASE_VERSION

# 使用阿里云镜像源解决网络问题（支持 x86_64 和 ARM64 架构）
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
  && sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
  && sed -i 's/ports.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash procps netcat-openbsd \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN set -eux; \
  out="hbase.tgz"; \
  # 优先尝试清华大学镜像源（国内加速）
  mirror_url="https://mirrors.tuna.tsinghua.edu.cn/apache/hbase/${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz"; \
  # 回退到 Apache 官方源
  official_url="https://archive.apache.org/dist/hbase/${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz"; \
  echo "尝试从清华镜像下载 HBase ${HBASE_VERSION}..."; \
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
  ln -s "hbase-${HBASE_VERSION}" hbase

COPY conf/hbase-site.xml /opt/hbase/conf/hbase-site.xml
COPY conf/hbase-env.sh /opt/hbase/conf/hbase-env.sh
COPY conf/regionservers /opt/hbase/conf/regionservers
COPY entrypoint.sh /entrypoint.sh
COPY init/ /init/

# 修复 Windows CRLF 换行符问题
RUN chmod +x /entrypoint.sh \
  && sed -i 's/\r$//' /entrypoint.sh \
  && sed -i 's/\r$//' /opt/hbase/conf/hbase-env.sh \
  && find /init -type f -name "*.sh" -exec sed -i 's/\r$//' {} \;

EXPOSE 16010 16000 16020 16030 16201

VOLUME ["/data"]

ENTRYPOINT ["/entrypoint.sh"]
