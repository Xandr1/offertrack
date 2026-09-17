# syntax=docker/dockerfile:1.7

FROM python:3.11.16-slim-trixie@sha256:d1053354624536b044162aaab1e418bd000ea35184fb1ae098ab3166b1072e72 AS dependencies

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

FROM python:3.11.16-slim-trixie@sha256:d1053354624536b044162aaab1e418bd000ea35184fb1ae098ab3166b1072e72 AS runtime

ENV HOST=0.0.0.0 \
    PATH="/opt/venv/bin:${PATH}" \
    PORT=8000 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VIRTUAL_ENV=/opt/venv

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        gzip=1.13-1+deb13u1 \
        libpcre2-8-0=10.46-1~deb13u2 \
        libsqlite3-0=3.46.1-7+deb13u2 \
        perl-base=5.40.1-6+deb13u1 \
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
