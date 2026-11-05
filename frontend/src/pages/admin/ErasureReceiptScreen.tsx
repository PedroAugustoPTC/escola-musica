import { useParams } from 'react-router';
import { Button } from '../../components/Button';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { useErasureReceipt } from '../../services/admin/erasures';
import { formatDate, instantDate } from '../../services/formatting';

const sum = (byStatus: Record<string, number>) => Object.values(byStatus).reduce((total, count) => total + count, 0);

/**
 * A17b — Comprovante da eliminação, para imprimir. Mostra só o protocolo, as datas, o alcance e o
 * contato da LGPD: nunca nome, CPF nem dado do aluno ou de quem pediu (FR-025).
 */
export function ErasureReceiptScreen() {
  const { requestId } = useParams();
  const receipt = useErasureReceipt(requestId);

  return (
    <div className="page page--medium erasure-receipt">
      {receipt.isPending ? <LoadingState label="Carregando comprovante" rows={3} /> : null}
      {receipt.isError ? <ErrorState error={receipt.error} onRetry={() => void receipt.refetch()} /> : null}
      {receipt.data ? (
        <article aria-labelledby="receipt-title">
          <div className="kicker">Instituto Musical Felipe Blaft</div>
          <h1 id="receipt-title" className="page-title">
            Comprovante de eliminação de dados
          </h1>
          <dl className="erasure-receipt__data">
            <dt>Protocolo</dt>
            <dd>{receipt.data.protocolReference}</dd>
            <dt>Aluno (código)</dt>
            <dd>#{receipt.data.studentShortId.toUpperCase()}</dd>
            <dt>Pedido recebido em</dt>
            <dd>{formatDate(receipt.data.requestedOn)}</dd>
            <dt>Eliminação concluída em</dt>
            <dd>{instantDate(receipt.data.completedAt)}</dd>
            <dt>Relatórios de aula eliminados</dt>
            <dd>{sum(receipt.data.scope.lessonReports)}</dd>
            <dt>Relatórios semestrais eliminados</dt>
            <dd>{sum(receipt.data.scope.semesterReports)}</dd>
            <dt>Arquivos PDF eliminados</dt>
            <dd>{receipt.data.scope.pdfs}</dd>
          </dl>
          <p>
            Contato do responsável pela LGPD: {receipt.data.privacyContact.name || 'a definir pela escola'}
            {receipt.data.privacyContact.contact ? ` · ${receipt.data.privacyContact.contact}` : ''}.
          </p>
          <div className="erasure-receipt__actions">
            <Button onClick={() => window.print()}>
              Imprimir
            </Button>
          </div>
        </article>
      ) : null}
    </div>
  );
}
