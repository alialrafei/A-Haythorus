FROM node:22-alpine AS ui-build

WORKDIR /ui

COPY jvm-frontend/package.json jvm-frontend/package-lock.json ./

RUN npm ci

COPY jvm-frontend/ ./

RUN npm run build:web


FROM maven:3.9-eclipse-temurin-25 AS java-build

WORKDIR /build

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests


FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY --from=java-build \
  /build/target/jvm-watcher-1.0-SNAPSHOT-V1.jar \
  /app/a-haythorus.jar

COPY --from=ui-build \
  /ui/dist-web \
  /app/ui

ENV AH_RUNTIME_MODE=local
ENV AH_SERVER_HOST=0.0.0.0
ENV AH_SERVER_PORT=8899
ENV AH_UI_DIR=/app/ui
LABEL version="v2.0.0"
EXPOSE 8899

ENTRYPOINT ["java", "-jar", "/app/a-haythorus.jar"]