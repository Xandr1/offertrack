# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645

RUN apk add --no-cache \
        libexpat=2.8.2-r0 \
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
