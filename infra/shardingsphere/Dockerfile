ARG SHARDINGSPHERE_VERSION=5.5.0
ARG MYSQL_CONNECTOR_VERSION=8.4.0

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-jammy

ARG SHARDINGSPHERE_VERSION
ARG MYSQL_CONNECTOR_VERSION

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash tini \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN set -eux; \
  url="https://archive.apache.org/dist/shardingsphere/${SHARDINGSPHERE_VERSION}/apache-shardingsphere-${SHARDINGSPHERE_VERSION}-shardingsphere-proxy-bin.tar.gz"; \
  out="proxy.tgz"; \
  i=0; \
  while [ "$i" -lt 12 ]; do \
    if curl -fL --http1.1 --connect-timeout 20 --max-time 1200 -C - -o "${out}" "${url}"; then \
      break; \
    fi; \
    i=$((i+1)); \
    sleep 2; \
  done; \
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
CMD ["/opt/shardingsphere-proxy/bin/start.sh"]
