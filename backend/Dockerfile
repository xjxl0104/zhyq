# ---- 构建阶段:Maven 打 jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷 pom 单独下依赖,利用镜像层缓存(改代码不重下依赖)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- 运行阶段:JRE + RapidOCR(ONNX OpenMP) 运行库 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 libstdc++6 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/tmp
COPY --from=build /build/target/zhyq-park.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-Djava.io.tmpdir=/app/tmp", "-jar", "app.jar"]
