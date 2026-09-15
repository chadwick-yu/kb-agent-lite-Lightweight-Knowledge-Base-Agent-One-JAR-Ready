FROM eclipse-temurin:17-jre

WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY target/kb-agent-lite.jar /app/kb-agent-lite.jar

# 数据目录（挂载卷持久化：H2库 / 上传文件 / 向量文件）
ENV KB_DATA_DIR=/app/data
VOLUME ["/app/data"]

EXPOSE 8090

ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "/app/kb-agent-lite.jar"]
