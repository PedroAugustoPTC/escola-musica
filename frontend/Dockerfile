# syntax=docker/dockerfile:1
# Imagem `web` (R-15): build do SPA com Node e entrega pelo Caddy. Contexto de build: raiz do repositório.

FROM node:24-alpine AS build
WORKDIR /repo/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
COPY backend/application/src/main/resources/messages/ /repo/backend/application/src/main/resources/messages/
COPY specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml /repo/specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml
COPY specs/005-admin-registrations-access/contracts/api-administration.openapi.yaml /repo/specs/005-admin-registrations-access/contracts/api-administration.openapi.yaml
COPY specs/006-admin-report-history/contracts/api-school-history.openapi.yaml /repo/specs/006-admin-report-history/contracts/api-school-history.openapi.yaml
COPY specs/007-privacy-retention-operations/contracts/api-privacy-operations.openapi.yaml /repo/specs/007-privacy-retention-operations/contracts/api-privacy-operations.openapi.yaml
RUN npm run build

FROM caddy:2
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/frontend/dist /srv
# SEC-004: o Caddy roda sem root. As portas 80/443 abrem pelo sysctl ip_unprivileged_port_start=0 do compose;
# volumes já existentes trocam de dono em infra/scripts/lib/web-volumes.sh.
RUN chown -R 1000:1000 /data /config
USER 1000:1000
