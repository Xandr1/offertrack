# syntax=docker/dockerfile:1.7

FROM python:3.11.15-slim-trixie@sha256:78b39ef14d8e2b4d71f8dc304f1328c37df95fe0ef99477c2ae6bd3d03784553 AS dependencies

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /build

RUN python -m venv /opt/venv

COPY pyproject.toml pylock.toml pylock.toml.sha256 ./

RUN sha256sum --check pylock.toml.sha256 \
    && /opt/venv/bin/python -m pip install --upgrade "pip==26.1.2" \
    && /opt/venv/bin/python -m pip install --requirement pylock.toml \
    && /opt/venv/bin/python -m pip uninstall --yes setuptools wheel \
    && /opt/venv/bin/python -m pip uninstall --yes pip

FROM python:3.11.15-slim-trixie@sha256:78b39ef14d8e2b4d71f8dc304f1328c37df95fe0ef99477c2ae6bd3d03784553 AS runtime

ENV HOST=0.0.0.0 \
    PATH="/opt/venv/bin:${PATH}" \
    PORT=8000 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VIRTUAL_ENV=/opt/venv

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        bsdutils=1:2.41.5-0+deb13u1 \
        libblkid1=2.41.5-0+deb13u1 \
        liblastlog2-2=2.41.5-0+deb13u1 \
        libmount1=2.41.5-0+deb13u1 \
        libsmartcols1=2.41.5-0+deb13u1 \
        libssl3t64=3.5.7-1~deb13u2 \
        libuuid1=2.41.5-0+deb13u1 \
        login=1:4.16.0-2+really2.41.5-0+deb13u1 \
        mount=2.41.5-0+deb13u1 \
        openssl=3.5.7-1~deb13u2 \
        openssl-provider-legacy=3.5.7-1~deb13u2 \
        util-linux=2.41.5-0+deb13u1 \
    && rm -rf /var/lib/apt/lists/* \
    && python -m pip uninstall --yes setuptools wheel \
    && python -m pip uninstall --yes pip \
    && groupadd --system --gid 10001 offertrack \
    && useradd --system --uid 10001 --gid offertrack --no-create-home \
        --home-dir /nonexistent --shell /usr/sbin/nologin offertrack

WORKDIR /app

COPY --from=dependencies /opt/venv /opt/venv
COPY --chown=10001:10001 app ./app
COPY --chown=10001:10001 --chmod=0555 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 8000

USER 10001:10001

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD []
