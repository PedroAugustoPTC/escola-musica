import { msg } from '../mensagens/mensagens';
import type { TrabalhoDto } from '../servicos/trabalhos';
import { Icone } from './Icone';

type Etapa = TrabalhoDto['etapa'];

/** Etapas mostradas para cada tipo de trabalho (R-06); a etapa atual é acrescentada se faltar. */
const ETAPAS_POR_TIPO: Record<TrabalhoDto['tipo'], Etapa[]> = {
  REGISTRO_AULA: ['ENVIANDO', 'TRANSCREVENDO', 'ESTRUTURANDO', 'PRONTO'],
  NOVA_TENTATIVA: ['ESTRUTURANDO', 'PRONTO'],
  RESPOSTA_PERGUNTAS: ['INCORPORANDO_RESPOSTAS', 'PRONTO'],
  ALTERACAO: ['APLICANDO_ALTERACAO', 'PRONTO'],
  CONSOLIDACAO_SEMESTRAL: ['CONSOLIDANDO', 'GERANDO_PDF', 'PRONTO'],
};

export function etapasDoTrabalho(tipo: TrabalhoDto['tipo'], atual: Etapa): Etapa[] {
  const etapas = [...ETAPAS_POR_TIPO[tipo]];
  if (!etapas.includes(atual)) {
    etapas.splice(Math.max(0, etapas.length - 1), 0, atual);
  }
  if (atual === 'TRANSCREVENDO_PEDIDO' && etapas[0] !== 'TRANSCREVENDO_PEDIDO') {
    etapas.splice(etapas.indexOf(atual), 1);
    etapas.unshift(atual);
  }
  return etapas;
}

/** Lista das etapas com a atual destacada (tela 06); a região é anunciada sem interromper. */
export function EtapasProcessamento({ trabalho }: { trabalho: Pick<TrabalhoDto, 'tipo' | 'etapa'> }) {
  const etapas = etapasDoTrabalho(trabalho.tipo, trabalho.etapa);
  const indiceAtual = etapas.indexOf(trabalho.etapa);
  return (
    <ol aria-label="Etapas do processamento" className="etapas">
      {etapas.map((etapa, indice) => {
        const concluida = indice < indiceAtual || (etapa === 'PRONTO' && indice === indiceAtual);
        const atual = indice === indiceAtual && etapa !== 'PRONTO';
        return (
          <li
            key={etapa}
            className={`etapas__item${atual ? ' etapas__item--atual' : ''}${concluida ? ' etapas__item--concluida' : ''}`}
            aria-current={atual ? 'step' : undefined}
          >
            <span className="etapas__barra" aria-hidden="true">
              <span className="etapas__progresso" />
            </span>
            <span className="etapas__rotulo">{msg(`ETAPA_${etapa}`)}</span>
            {concluida ? <Icone nome="check" rotulo="concluído" className="etapas__ok" /> : null}
          </li>
        );
      })}
    </ol>
  );
}
