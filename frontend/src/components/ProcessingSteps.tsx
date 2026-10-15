import { msg } from '../messages/messages';
import type { JobDto } from '../services/jobs';
import { Icon } from './Icon';

type Step = JobDto['step'];

/** Etapas mostradas para cada tipo de trabalho (R-06); a etapa atual é acrescentada se faltar. */
const STEPS_BY_TYPE: Record<JobDto['type'], Step[]> = {
  LESSON_REGISTRATION: ['UPLOADING', 'TRANSCRIBING', 'STRUCTURING', 'READY'],
  RETRY: ['STRUCTURING', 'READY'],
  QUESTION_ANSWER: ['INCORPORATING_ANSWERS', 'READY'],
  CHANGE: ['APPLYING_CHANGE', 'READY'],
  SEMESTER_CONSOLIDATION: ['CONSOLIDATING', 'GENERATING_PDF', 'READY'],
};

export function jobSteps(type: JobDto['type'], current: Step): Step[] {
  const steps = [...STEPS_BY_TYPE[type]];
  if (!steps.includes(current)) {
    steps.splice(Math.max(0, steps.length - 1), 0, current);
  }
  if (current === 'TRANSCRIBING_REQUEST' && steps[0] !== 'TRANSCRIBING_REQUEST') {
    steps.splice(steps.indexOf(current), 1);
    steps.unshift(current);
  }
  return steps;
}

/** Lista das etapas com a atual destacada (tela 06); a região é anunciada sem interromper. */
export function ProcessingSteps({ job }: { job: Pick<JobDto, 'type' | 'step'> }) {
  const steps = jobSteps(job.type, job.step);
  const currentIndex = steps.indexOf(job.step);
  return (
    <ol aria-label="Etapas do processamento" className="steps">
      {steps.map((step, index) => {
        const completed = index < currentIndex || (step === 'READY' && index === currentIndex);
        const current = index === currentIndex && step !== 'READY';
        return (
          <li
            key={step}
            className={`steps__item${current ? ' steps__item--current' : ''}${completed ? ' steps__item--completed' : ''}`}
            aria-current={current ? 'step' : undefined}
          >
            <span className="steps__bar" aria-hidden="true">
              <span className="steps__progress" />
            </span>
            <span className="steps__label">{msg(`STEP_${step}`)}</span>
            {completed ? <Icon name="check" label="concluído" className="steps__ok" /> : null}
          </li>
        );
      })}
    </ol>
  );
}
