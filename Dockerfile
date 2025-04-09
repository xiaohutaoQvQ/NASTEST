FROM eclipse-temurin:17-jdk-jammy

# 设置工作目录
WORKDIR /app

# 复制 Maven 打包生成的 JAR 文件到镜像中------
COPY target/NasTest-0.0.1-SNAPSHOT.jar app.jar

# 暴露 Spring Boot 默认端口（根据实际端口修改）
EXPOSE 9008

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]