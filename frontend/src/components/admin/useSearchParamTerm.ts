import { useEffect, useState } from 'react';
import type { SetURLSearchParams } from 'react-router';
import { looksLikeCpf } from '../student/cpfShape';

/**
 * Lê o parâmetro `search` da URL sem nunca deixar passar um CPF. Um termo com forma de CPF é descartado:
 * a URL é limpa com `replace` (sai do histórico), o termo devolvido é vazio (não entra na chave de consulta
 * nem na API) e `refusedFromUrl` fica verdadeiro até `dismissRefusal`, para a tela mostrar o aviso de recusa.
 */
export function useSearchParamTerm(params: URLSearchParams, setParams: SetURLSearchParams) {
  const raw = params.get('search') ?? '';
  const forbidden = looksLikeCpf(raw.trim());
  const [refusedFromUrl, setRefusedFromUrl] = useState(forbidden);
  if (forbidden && !refusedFromUrl) setRefusedFromUrl(true);

  useEffect(() => {
    if (!forbidden) return;
    const next = new URLSearchParams(params);
    next.delete('search');
    setParams(next, { replace: true });
  }, [forbidden, params, setParams]);

  return {
    search: forbidden ? '' : raw,
    refusedFromUrl,
    dismissRefusal: () => setRefusedFromUrl(false),
  };
}
