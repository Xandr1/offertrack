#!/bin/sh

set -eu

if [ -z "${HOST:-}" ]; then
  echo "HOST must not be empty." >&2
  exit 1
fi

case "${PORT:-}" in
  "" | *[!0-9]*)
    echo "PORT must be an integer from 1 through 65535." >&2
    exit 1
    ;;
esac

if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "PORT must be an integer from 1 through 65535." >&2
  exit 1
fi

exec /opt/venv/bin/python -m uvicorn app.main:app --host "$HOST" --port "$PORT"
