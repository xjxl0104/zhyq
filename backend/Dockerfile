# ---- 构建阶段:Maven 打 jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷 pom 单独下依赖,利用镜像层缓存(改代码不重下依赖)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- 运行阶段:仅 JRE ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/zhyq-park.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
