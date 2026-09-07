# syntax=docker/dockerfile:1.7

FROM --platform=linux/amd64 node:24.18.0-bookworm-slim@sha256:d45d78e7929b46875bbd4e29bea672d5bc48186c6c3588306521c815e78352d6 AS base

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm install --global pnpm@12.3.4 \
    && pnpm --version | grep -Fx '12.3.4'

WORKDIR /workspace

FROM base AS dependencies

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json ./apps/web/package.json
COPY apps/web/vendor/lucide-react-0.542.0.tgz ./apps/web/vendor/lucide-react-0.542.0.tgz

RUN pnpm install --frozen-lockfile

FROM dependencies AS builder

COPY apps/web/app ./apps/web/app
COPY apps/web/public ./apps/web/public
COPY apps/web/src ./apps/web/src
COPY apps/web/next.config.ts ./apps/web/next.config.ts
COPY apps/web/postcss.config.mjs ./apps/web/postcss.config.mjs
COPY apps/web/tsconfig.json ./apps/web/tsconfig.json

ARG APP_ENV
ARG NEXT_PUBLIC_API_URL

RUN test -n "$APP_ENV" \
    && test -n "$NEXT_PUBLIC_API_URL" \
    && APP_ENV="$APP_ENV" NEXT_PUBLIC_API_URL="$NEXT_PUBLIC_API_URL" \
      pnpm --dir apps/web run build

FROM --platform=linux/amd64 node:24.18.0-bookworm-slim@sha256:d45d78e7929b46875bbd4e29bea672d5bc48186c6c3588306521c815e78352d6 AS runtime

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000

RUN rm -rf \
      /usr/local/lib/node_modules/npm \
      /usr/local/lib/node_modules/corepack \
      /opt/yarn-v1.22.22 \
    && rm -f \
      /usr/local/bin/npm \
      /usr/local/bin/npx \
      /usr/local/bin/corepack \
      /usr/local/bin/pnpm \
      /usr/local/bin/pnpx \
      /usr/local/bin/yarn \
      /usr/local/bin/yarnpkg

COPY --from=builder --chown=1000:1000 /workspace/apps/web/.next/standalone/ /app/
COPY --from=builder --chown=1000:1000 /workspace/apps/web/public/ /app/apps/web/public/
COPY --from=builder --chown=1000:1000 /workspace/apps/web/.next/static/ /app/apps/web/.next/static/

WORKDIR /app/apps/web
USER 1000:1000

EXPOSE 3000

CMD ["node", "server.js"]
