ARG FLINK_VERSION=1.20.0
FROM public.ecr.aws/docker/library/flink:${FLINK_VERSION}-scala_2.12-java17

ARG KAFKA_CONNECTOR_VERSION=3.4.0-1.20

RUN set -eux; \
  curl -fL --retry 8 --retry-all-errors --retry-delay 2 --connect-timeout 20 --max-time 300 \
    -o "/opt/flink/lib/flink-sql-connector-kafka-${KAFKA_CONNECTOR_VERSION}.jar" \
    "https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-kafka/${KAFKA_CONNECTOR_VERSION}/flink-sql-connector-kafka-${KAFKA_CONNECTOR_VERSION}.jar"
