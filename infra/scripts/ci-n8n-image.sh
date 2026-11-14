#!/bin/sh
# Deixa a imagem base do n8n no Docker local ANTES do build, para o CI não depender de um registro só (o docker.n8n.io
# responde 429 aos executores do GitHub). Lê a tag e o digest fixados em infra/n8n/Dockerfile.
#
#   sh infra/scripts/ci-n8n-image.sh [arquivo-de-cache.tar]
#
# 1. se o arquivo de cache existe, carrega a imagem dele;
# 2. senão baixa POR DIGEST (conteúdo verificado) do docker.n8n.io, do ghcr.io e do Docker Hub, nessa ordem, repetindo
#    cada um até 3 vezes; marca a imagem com a tag do Dockerfile e, se houver caminho de cache, salva o arquivo.
# Com a imagem já no Docker local, o build do Dockerfile não consulta o registro.
set -eu

dockerfile="${DOCKERFILE:-infra/n8n/Dockerfile}"
cache="${1:-}"

image="$(sed -n 's/^FROM \(docker\.n8n\.io\/[^:@ ]*\):\([^ @]*\).*$/\1/p' "$dockerfile" | head -n 1)"
tag="$(sed -n 's/^FROM docker\.n8n\.io\/[^:@ ]*:\([^ @]*\).*$/\1/p' "$dockerfile" | head -n 1)"
digest="$(sed -n 's/^# digest \(sha256:[0-9a-f]*\).*$/\1/p' "$dockerfile" | head -n 1)"
if [ -z "$image" ] || [ -z "$tag" ] || [ -z "$digest" ]; then
  echo "ci-n8n-image: não achei imagem, tag e digest em $dockerfile." >&2
  exit 1
fi
target="$image:$tag"

if docker image inspect "$target" --format '{{join .RepoDigests " "}}' 2>/dev/null | grep -q "$digest"; then
  echo "ci-n8n-image: $target já está no Docker local (digest conferido)."
  exit 0
fi

if [ -n "$cache" ] && [ -s "$cache" ]; then
  echo "ci-n8n-image: carregando $target do cache."
  docker load -i "$cache"
  exit 0
fi

for source in "$image" ghcr.io/n8n-io/n8n n8nio/n8n; do
  attempt=1
  while [ "$attempt" -le 3 ]; do
    if docker pull "$source@$digest"; then
      docker tag "$source@$digest" "$target"
      if [ -n "$cache" ]; then
        docker save -o "$cache" "$target"
      fi
      echo "ci-n8n-image: $target pronta (origem: $source)."
      exit 0
    fi
    echo "ci-n8n-image: falha ao baixar de $source (tentativa $attempt de 3)." >&2
    sleep $((attempt * 15))
    attempt=$((attempt + 1))
  done
done
echo "ci-n8n-image: nenhum registro entregou a imagem do n8n." >&2
exit 1
