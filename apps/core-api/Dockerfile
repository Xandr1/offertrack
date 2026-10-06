# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25.0.4.1_1-jre-alpine-3.23@sha256:d14b5b3cb1464cdbf81e0b8a0a6f45f308dcf08d8aea97934569f1237a167ded

RUN apk add --no-cache \
        libcrypto3=3.5.9-r0 \
        libexpat=2.8.5-r0 \
        libssl3=3.5.9-r0 \
        openssl=3.5.9-r0 \
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
