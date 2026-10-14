import { useState } from 'react';
import { msg } from '../mensagens/mensagens';
import { abrirPdf, usePdf } from '../servicos/pdf';
import { Botao } from './Botao';
import { AlertaErro } from './EstadoErro';

/**
 * PDF de conferência (tela 08, 08b): baixado por fetch autenticado e mostrado num `iframe` com URL
 * `blob:`. Enquanto o PDF não existe, mostra que ainda está sendo gerado; "Abrir PDF" abre numa nova
 * aba ou, se bloqueada, baixa o arquivo.
 */
export function VisualizadorPdf({
  caminho,
  versao,
  disponivel,
  nomeArquivo,
}: {
  caminho: string;
  versao: number;
  disponivel: boolean;
  nomeArquivo: string;
}) {
  const { url, erro } = usePdf(disponivel ? caminho : null);
  const [aviso, setAviso] = useState<string | null>(null);

  const abrir = async () => {
    setAviso(null);
    try {
      const abriu = await abrirPdf(caminho, nomeArquivo);
      if (!abriu) setAviso(msg('PDF_BLOCKED_BY_BROWSER'));
    } catch (falha) {
      setAviso(falha instanceof Error ? falha.message : msg('INTERNAL_ERROR'));
    }
  };

  return (
    <div className="visualizador-pdf">
      <div className="visualizador-pdf__topo">
        <span className="visualizador-pdf__rotulo">PDF · VERSÃO {versao}</span>
        {disponivel ? (
          <Botao variante="link" icone="open_in_new" onClick={() => void abrir()}>
            {msg('OPEN_PDF_BUTTON')}
          </Botao>
        ) : null}
      </div>
      {aviso ? (
        <div role="status" className="alerta alerta--info">
          <div className="alerta__texto">{aviso}</div>
        </div>
      ) : null}
      {!disponivel ? (
        <div role="status" className="visualizador-pdf__gerando">
          <span className="girador girador--grande" aria-hidden="true" />
          <div className="visualizador-pdf__gerando-titulo">{msg('REVIEW_PDF_GENERATING')}</div>
          <div className="texto-apoio">Você já pode conferir o texto enquanto isso.</div>
        </div>
      ) : erro ? (
        <AlertaErro erro={erro} />
      ) : url ? (
        <iframe className="visualizador-pdf__quadro" src={url} title={`PDF do relatório, versão ${versao}`} />
      ) : (
        <div className="visualizador-pdf__gerando" aria-busy="true">
          <span className="girador girador--grande" aria-hidden="true" />
        </div>
      )}
    </div>
  );
}
