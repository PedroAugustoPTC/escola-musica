import { useState } from 'react';
import { msg } from '../messages/messages';
import { openPdf, usePdf } from '../services/pdf';
import { Button } from './Button';
import { ErrorAlert } from './ErrorState';

/**
 * PDF de conferência (tela 08, 08b): baixado por fetch autenticado e mostrado num `iframe` com URL
 * `blob:`. Enquanto o PDF não existe, mostra que ainda está sendo gerado; "Abrir PDF" abre numa nova
 * aba ou, se bloqueada, baixa o arquivo.
 */
export function PdfViewer({
  path,
  version,
  available,
  fileName,
}: {
  path: string;
  version: number;
  available: boolean;
  fileName: string;
}) {
  const { url, error } = usePdf(available ? path : null);
  const [notice, setNotice] = useState<string | null>(null);

  const open = async () => {
    setNotice(null);
    try {
      const opened = await openPdf(path, fileName);
      if (!opened) setNotice(msg('PDF_BLOCKED_BY_BROWSER'));
    } catch (failure) {
      setNotice(failure instanceof Error ? failure.message : msg('INTERNAL_ERROR'));
    }
  };

  return (
    <div className="pdf-viewer">
      <div className="pdf-viewer__top">
        <span className="pdf-viewer__label">PDF · VERSÃO {version}</span>
        {available ? (
          <Button variant="link" icon="open_in_new" onClick={() => void open()}>
            {msg('OPEN_PDF_BUTTON')}
          </Button>
        ) : null}
      </div>
      {notice ? (
        <div role="status" className="alert alert--info">
          <div className="alert__text">{notice}</div>
        </div>
      ) : null}
      {!available ? (
        <div role="status" className="pdf-viewer__generating">
          <span className="spinner spinner--large" aria-hidden="true" />
          <div className="pdf-viewer__generating-title">{msg('REVIEW_PDF_GENERATING')}</div>
          <div className="support-text">Você já pode conferir o texto enquanto isso.</div>
        </div>
      ) : error ? (
        <ErrorAlert error={error} />
      ) : url ? (
        <iframe className="pdf-viewer__frame" src={url} title={`PDF do relatório, versão ${version}`} />
      ) : (
        <div className="pdf-viewer__generating" aria-busy="true">
          <span className="spinner spinner--large" aria-hidden="true" />
        </div>
      )}
    </div>
  );
}
