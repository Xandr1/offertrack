FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY pyproject.toml pylock.toml pylock.toml.sha256 ./
COPY app ./app

RUN sha256sum --check pylock.toml.sha256 \
    && python -m pip install --no-cache-dir --upgrade "pip==26.1.2" \
    && python -m pip install --no-cache-dir --requirement pylock.toml \
    && groupadd --system --gid 10001 offertrack \
    && useradd --system --uid 10001 --gid offertrack --no-create-home \
        --home-dir /nonexistent --shell /usr/sbin/nologin offertrack

EXPOSE 8000

USER 10001:10001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
