# syntax=docker/dockerfile:1
# Imagem `web` (R-15): build do SPA com Node e entrega pelo Caddy. Contexto de build: raiz do repositório.

FROM node:24-alpine AS build
WORKDIR /repo/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
COPY backend/aplicacao/src/main/resources/mensagens/ /repo/backend/aplicacao/src/main/resources/mensagens/
COPY specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml /repo/specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml
RUN npm run build

FROM caddy:2
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/frontend/dist /srv
