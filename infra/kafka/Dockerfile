ARG KAFKA_VERSION=3.8.1
ARG SCALA_VERSION=2.13

FROM eclipse-temurin:21-jre-jammy

ARG KAFKA_VERSION
ARG SCALA_VERSION

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl bash tini \
  && rm -rf /var/lib/apt/lists/*

RUN useradd -r -m -U -s /usr/sbin/nologin kafka

WORKDIR /opt
RUN set -eux; \
  url="https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"; \
  out="kafka.tgz"; \
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
  ln -s "kafka_${SCALA_VERSION}-${KAFKA_VERSION}" kafka; \
  chown -R kafka:kafka "/opt/kafka_${SCALA_VERSION}-${KAFKA_VERSION}"

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 9092 9093

USER kafka
VOLUME ["/var/lib/kafka"]

ENTRYPOINT ["/usr/bin/tini","--","/entrypoint.sh"]
