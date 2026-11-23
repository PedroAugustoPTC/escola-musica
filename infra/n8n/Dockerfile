# Imagem fina do n8n (feature 004, R-01 e R-04): a imagem oficial com os workflows e os utilitários do
# repositório embutidos. É a mesma imagem no ambiente local, nos testes e na produção; só a configuração varia.
# Segredos nunca entram na imagem: chegam por variável de ambiente (infra/.env) em tempo de execução.
#
# Versão FIXADA (nunca latest, stable nem next). Para atualizar, siga a seção "Versão do n8n" de infra/README.md.
# digest sha256:cbfb9e50b10521a9cdc94147e570df9051a885e5a8b08bc540f5e51df5a85e1f (linux/amd64)
FROM docker.n8n.io/n8nio/n8n:2.49.5

COPY --chown=node:node workflows /opt/workflows
COPY --chown=node:node ops /opt/ops
COPY --chown=node:node --chmod=0755 entrypoint.sh /opt/entrypoint.sh

USER node

# O tini da imagem original continua como PID 1 (encaminha sinais e recolhe processos filhos).
ENTRYPOINT ["tini", "--", "/opt/entrypoint.sh"]
