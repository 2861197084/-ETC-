# 阶段1: Maven 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY settings.xml .
COPY src ./src

# 直接编译打包
RUN mvn -s settings.xml clean package -DskipTests -B -e

# 阶段2: 运行时
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
