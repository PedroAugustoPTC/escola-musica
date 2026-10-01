import { useEffect, useState } from 'react';
import { requisitarBlob } from './clienteApi';

/**
 * Baixa o PDF por fetch autenticado e devolve uma URL `blob:` para o visualizador. A URL é revogada
 * ao trocar de documento ou desmontar (a rota do PDF exige o cookie de sessão, R-11).
 */
export function usePdf(caminho: string | null) {
  const [estado, setEstado] = useState<{ caminho: string; url: string | null; erro: unknown }>({
    caminho: '',
    url: null,
    erro: null,
  });

  useEffect(() => {
    if (!caminho) return undefined;
    const controle = new AbortController();
    let criada: string | null = null;
    requisitarBlob(caminho, controle.signal)
      .then((blob) => {
        criada = URL.createObjectURL(blob);
        setEstado({ caminho, url: criada, erro: null });
      })
      .catch((falha: unknown) => {
        if (!(falha instanceof DOMException && falha.name === 'AbortError')) {
          setEstado({ caminho, url: null, erro: falha });
        }
      });
    return () => {
      controle.abort();
      if (criada) URL.revokeObjectURL(criada);
    };
  }, [caminho]);

  const atual = Boolean(caminho) && estado.caminho === caminho;
  return {
    url: atual ? estado.url : null,
    erro: atual ? estado.erro : null,
    carregando: Boolean(caminho) && !atual,
  };
}

/**
 * Abre o PDF numa nova aba; se o navegador bloquear, baixa o arquivo e devolve `false` para que a
 * tela mostre `PDF_BLOQUEADO_NAVEGADOR`.
 */
export async function abrirPdf(caminho: string, nomeArquivo: string): Promise<boolean> {
  const aba = window.open('', '_blank');
  const blob = await requisitarBlob(caminho);
  const url = URL.createObjectURL(blob);
  if (aba) {
    aba.location.href = url;
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return true;
  }
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  return false;
}
