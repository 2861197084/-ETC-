ARG SHARDINGSPHERE_VERSION=5.4.1
ARG MYSQL_CONNECTOR_VERSION=8.4.0

FROM eclipse-temurin:21-jre-jammy

ARG SHARDINGSPHERE_VERSION
ARG MYSQL_CONNECTOR_VERSION

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash tini \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN set -eux; \
  out="proxy.tgz"; \
  # 优先尝试清华大学镜像源（国内加速）
  mirror_url="https://mirrors.tuna.tsinghua.edu.cn/apache/shardingsphere/${SHARDINGSPHERE_VERSION}/apache-shardingsphere-${SHARDINGSPHERE_VERSION}-shardingsphere-proxy-bin.tar.gz"; \
  # 回退到 Apache 官方源
  official_url="https://archive.apache.org/dist/shardingsphere/${SHARDINGSPHERE_VERSION}/apache-shardingsphere-${SHARDINGSPHERE_VERSION}-shardingsphere-proxy-bin.tar.gz"; \
  echo "尝试从清华镜像下载 ShardingSphere ${SHARDINGSPHERE_VERSION}..."; \
  if curl -fL --http1.1 --connect-timeout 10 --max-time 300 -o "${out}" "${mirror_url}"; then \
  echo "清华镜像下载成功！"; \
  else \
  echo "清华镜像不可用，回退到 Apache 官方源..."; \
  i=0; \
  while [ "$i" -lt 12 ]; do \
  if curl -fL --http1.1 --connect-timeout 20 --max-time 1200 -C - -o "${out}" "${official_url}"; then \
  break; \
  fi; \
  i=$((i+1)); \
  sleep 2; \
  done; \
  fi; \
  test -s "${out}"; \
  tar -xzf "${out}"; \
  rm "${out}"; \
  ln -s "apache-shardingsphere-${SHARDINGSPHERE_VERSION}-shardingsphere-proxy-bin" shardingsphere-proxy; \
  mkdir -p /opt/shardingsphere-proxy/ext-lib; \
  curl -fL --http1.1 --connect-timeout 20 --max-time 300 --retry 8 --retry-all-errors --retry-delay 2 \
  -o "/opt/shardingsphere-proxy/ext-lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
  "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"

COPY conf/ /opt/shardingsphere-proxy/conf/

EXPOSE 3307

ENTRYPOINT ["/usr/bin/tini","--"]
CMD ["java", "-classpath", "/opt/shardingsphere-proxy/conf:/opt/shardingsphere-proxy/lib/*:/opt/shardingsphere-proxy/ext-lib/*", "-Xms512m", "-Xmx1024m", "org.apache.shardingsphere.proxy.Bootstrap", "3307", "/opt/shardingsphere-proxy/conf"]
