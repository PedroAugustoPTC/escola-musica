import { msg } from '../messages/messages';
import type { LessonReportDto } from '../services/lessonReports';

type Content = LessonReportDto['content'];

interface Section {
  title: string;
  items?: string[];
  text?: string;
}

/** As cinco seções do relatório de aula (FR-019), na ordem do modelo. */
export function lessonSections(content: Content): Section[] {
  return [
    { title: msg('WORKED_TOPICS_SECTION'), items: content.workedTopics },
    { title: msg('PROGRESS_SECTION'), text: content.progress },
    { title: msg('DIFFICULTIES_SECTION'), items: content.difficulties },
    { title: msg('PROPOSED_ACTIVITIES_SECTION'), items: content.proposedActivities },
    { title: msg('OBSERVATIONS_SECTION'), text: content.observations },
  ];
}

/** Relatório em texto (tela 08): campos sem informação aparecem como "—" (FR-020). */
export function ReportSections({ content, faded = false }: { content: Content; faded?: boolean }) {
  return (
    <article aria-label="Relatório em texto" className={`sections${faded ? ' sections--faded' : ''}`}>
      {lessonSections(content).map((section) => {
        const empty = section.items ? section.items.length === 0 : !section.text?.trim();
        return (
          <section key={section.title} className="sections__section">
            <h2 className="sections__title">{section.title}</h2>
            {empty ? (
              <p className="sections__empty">{msg('EMPTY_SECTION')}</p>
            ) : section.items ? (
              <ul className="sections__list">
                {section.items.map((item, index) => (
                  <li key={`${index}-${item}`}>
                    <span className="sections__marker" aria-hidden="true" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="sections__text">{section.text}</p>
            )}
          </section>
        );
      })}
    </article>
  );
}
