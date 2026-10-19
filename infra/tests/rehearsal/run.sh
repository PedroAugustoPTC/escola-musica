#!/bin/sh
# Roda os roteiros do ensaio em sequência (feature 004, R-12). Chamado como comando do serviço tests-rehearsal.
cd /work || exit 1
status=0
for script in rotation.sh switch.sh switch-refusals.sh; do
  [ -f "infra/tests/rehearsal/$script" ] || continue
  echo
  echo "════ roteiro $script"
  sh "infra/tests/rehearsal/$script" || status=1
done
exit $status
