# Simulador do Telegram dos ensaios (feature 004, R-12): SÓ para testes, nunca em produção. Sem dependências.
FROM node:24.15.0-alpine
WORKDIR /app
COPY server.mjs server.test.mjs ./
USER node
EXPOSE 8081
CMD ["node", "server.mjs"]
