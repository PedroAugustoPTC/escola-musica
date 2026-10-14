import { msg } from '../mensagens/mensagens';
import type { TrabalhoDto } from '../servicos/trabalhos';
import { Icone } from './Icone';

type Etapa = TrabalhoDto['step'];

/** Etapas mostradas para cada tipo de trabalho (R-06); a etapa atual é acrescentada se faltar. */
const ETAPAS_POR_TIPO: Record<TrabalhoDto['type'], Etapa[]> = {
  LESSON_REGISTRATION: ['UPLOADING', 'TRANSCRIBING', 'STRUCTURING', 'READY'],
  RETRY: ['STRUCTURING', 'READY'],
  QUESTION_ANSWER: ['INCORPORATING_ANSWERS', 'READY'],
  CHANGE: ['APPLYING_CHANGE', 'READY'],
  SEMESTER_CONSOLIDATION: ['CONSOLIDATING', 'GENERATING_PDF', 'READY'],
};

export function etapasDoTrabalho(tipo: TrabalhoDto['type'], atual: Etapa): Etapa[] {
  const etapas = [...ETAPAS_POR_TIPO[tipo]];
  if (!etapas.includes(atual)) {
    etapas.splice(Math.max(0, etapas.length - 1), 0, atual);
  }
  if (atual === 'TRANSCRIBING_REQUEST' && etapas[0] !== 'TRANSCRIBING_REQUEST') {
    etapas.splice(etapas.indexOf(atual), 1);
    etapas.unshift(atual);
  }
  return etapas;
}

/** Lista das etapas com a atual destacada (tela 06); a região é anunciada sem interromper. */
export function EtapasProcessamento({ trabalho }: { trabalho: Pick<TrabalhoDto, 'type' | 'step'> }) {
  const etapas = etapasDoTrabalho(trabalho.type, trabalho.step);
  const indiceAtual = etapas.indexOf(trabalho.step);
  return (
    <ol aria-label="Etapas do processamento" className="etapas">
      {etapas.map((etapa, indice) => {
        const concluida = indice < indiceAtual || (etapa === 'READY' && indice === indiceAtual);
        const atual = indice === indiceAtual && etapa !== 'READY';
        return (
          <li
            key={etapa}
            className={`etapas__item${atual ? ' etapas__item--atual' : ''}${concluida ? ' etapas__item--concluida' : ''}`}
            aria-current={atual ? 'step' : undefined}
          >
            <span className="etapas__barra" aria-hidden="true">
              <span className="etapas__progresso" />
            </span>
            <span className="etapas__rotulo">{msg(`STEP_${etapa}`)}</span>
            {concluida ? <Icone nome="check" rotulo="concluído" className="etapas__ok" /> : null}
          </li>
        );
      })}
    </ol>
  );
}
