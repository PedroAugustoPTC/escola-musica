import { useEffect, useState } from 'react';
import { requestBlob } from './apiClient';

/**
 * Baixa o PDF por fetch autenticado e devolve uma URL `blob:` para o visualizador. A URL é revogada
 * ao trocar de documento ou desmontar (a rota do PDF exige o cookie de sessão, R-11).
 */
export function usePdf(path: string | null) {
  const [state, setState] = useState<{ path: string; url: string | null; error: unknown }>({
    path: '',
    url: null,
    error: null,
  });

  useEffect(() => {
    if (!path) return undefined;
    const control = new AbortController();
    let created: string | null = null;
    requestBlob(path, control.signal)
      .then((blob) => {
        created = URL.createObjectURL(blob);
        setState({ path, url: created, error: null });
      })
      .catch((failure: unknown) => {
        if (!(failure instanceof DOMException && failure.name === 'AbortError')) {
          setState({ path, url: null, error: failure });
        }
      });
    return () => {
      control.abort();
      if (created) URL.revokeObjectURL(created);
    };
  }, [path]);

  const current = Boolean(path) && state.path === path;
  return {
    url: current ? state.url : null,
    error: current ? state.error : null,
    loading: Boolean(path) && !current,
  };
}

/**
 * Abre o PDF numa nova aba; se o navegador bloquear, baixa o arquivo e devolve `false` para que a
 * tela mostre `PDF_BLOQUEADO_NAVEGADOR`.
 */
export async function openPdf(path: string, fileName: string): Promise<boolean> {
  const tab = window.open('', '_blank');
  const blob = await requestBlob(path);
  const url = URL.createObjectURL(blob);
  if (tab) {
    tab.location.href = url;
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return true;
  }
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  return false;
}
