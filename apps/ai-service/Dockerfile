# syntax=docker/dockerfile:1.7

FROM python:3.11.15-slim-trixie@sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045 AS dependencies

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /build

RUN python -m venv /opt/venv

COPY pyproject.toml pylock.toml pylock.toml.sha256 ./

RUN sha256sum --check pylock.toml.sha256 \
    && /opt/venv/bin/python -m pip install --upgrade "pip==26.1.2" \
    && /opt/venv/bin/python -m pip install --requirement pylock.toml

FROM python:3.11.15-slim-trixie@sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045 AS runtime

ENV HOST=0.0.0.0 \
    PATH="/opt/venv/bin:${PATH}" \
    PORT=8000 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VIRTUAL_ENV=/opt/venv

RUN groupadd --system --gid 10001 offertrack \
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
