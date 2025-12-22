# 阶段1: Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY settings.xml .
COPY src ./src

# 直接编译打包
RUN mvn -s settings.xml clean package -DskipTests -B -e

# 阶段2: 运行时
# 说明：部分网络环境下拉取 eclipse-temurin:17-jre 容易失败，这里复用已存在的 JDK 基础镜像
# （体积更大，但可保证 `docker compose up -d --build` 顺利完成）。
FROM maven:3.9-eclipse-temurin-17

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
