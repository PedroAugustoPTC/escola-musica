#!/bin/sh
# Aviso de revisão do inventário de tratamento (FR-071). Modo aviso: nunca reprova o CI.
# Mesma regra de InventoryReviewGuard (backend/application/src/test/.../architecture).
# Uso: sh infra/scripts/check-inventory-review.sh [base]   (base padrão: origin/main)
set -eu

base="${1:-origin/main}"
changed="$(git diff --name-only "$base"...HEAD 2>/dev/null || true)"

if printf '%s\n' "$changed" | grep -qx 'privacy/processing-inventory.md'; then
  exit 0
fi

touched="$(printf '%s\n' "$changed" | grep -E '^backend/application/src/main/(java/br/com/felipeblaft/reports/outbound/ai/|resources/prompts/)' || true)"
if [ -n "$touched" ]; then
  echo "::warning title=Inventário de tratamento::Mudou o que vai a um provedor sem revisar privacy/processing-inventory.md."
  printf '%s\n' "$touched"
fi
exit 0
