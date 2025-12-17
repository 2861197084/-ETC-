ARG HBASE_VERSION=2.6.1

FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-jammy

ARG HBASE_VERSION

RUN apt-get update \
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

RUN chmod +x /entrypoint.sh

EXPOSE 16010 16000 16020 16030 16201

VOLUME ["/data"]

ENTRYPOINT ["/entrypoint.sh"]
