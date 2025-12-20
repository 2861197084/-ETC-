ARG HBASE_VERSION=2.6.1

FROM docker.io/library/eclipse-temurin:17-jre-jammy

ARG HBASE_VERSION

# 使用阿里云镜像源解决网络问题
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
  && sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash procps netcat-openbsd \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN set -eux; \
  url="https://archive.apache.org/dist/hbase/${HBASE_VERSION}/hbase-${HBASE_VERSION}-bin.tar.gz"; \
  out="hbase.tgz"; \
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
