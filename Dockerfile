# build-less development image (uses host gradle wrapper)
FROM eclipse-temurin:21-jdk
WORKDIR /app
# gradle wrapper 実行に必要なら追加
COPY . /app
RUN chmod +x ./gradlew || true
CMD ["./gradlew", "bootRun"]
