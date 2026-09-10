# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21.0.12_8-jre-alpine-3.23@sha256:1c59e0666519c026978ef64b429dfb78518d013d51f5056530f0daa57f2a5bcb

RUN apk add --no-cache \
        libcrypto3=3.5.8-r0 \
        libexpat=2.8.4-r0 \
        libssl3=3.5.8-r0 \
        openssl=3.5.8-r0 \
        p11-kit=0.26.2-r0 \
        p11-kit-trust=0.26.2-r0 \
    && addgroup -S -g 10001 offertrack \
    && adduser -S -D -H -u 10001 -G offertrack offertrack

WORKDIR /app

COPY --chown=10001:10001 --chmod=0444 app.jar /app/app.jar

ENV SERVER_PORT=8080

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
