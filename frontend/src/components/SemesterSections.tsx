import { msg } from '../messages/messages';
import type { SemesterSection } from '../services/semester';

/**
 * As 10 seções do semestral (tela 11): a seção 1 (Informações Gerais) é preenchida pelo sistema e
 * só leitura; campos sem informação aparecem como "—" (FR-020, FR-044).
 */
export function SemesterSections({
  sections,
  faded = false,
}: {
  sections: SemesterSection[];
  faded?: boolean;
}) {
  return (
    <article aria-label="Relatório em texto" className={`sections${faded ? ' sections--faded' : ''}`}>
      {sections.map((section) => (
        <section key={section.number} id={`section-${section.number}`} className="sections__section">
          <h2 className="sections__title sections__title--numbered">
            <span className="sections__number">{section.number}</span>
            {section.title}
          </h2>
          {section.fields.map((field, index) => {
            const empty = field.items.length === 0 && !field.text.trim();
            return (
              <div key={`${section.number}-${index}`} className="sections__field">
                {field.label ? <div className="sections__label">{field.label}</div> : null}
                {empty ? (
                  <p className="sections__empty">{msg('EMPTY_SECTION')}</p>
                ) : field.items.length > 0 ? (
                  <ul className="sections__list">
                    {field.items.map((item, i) => (
                      <li key={`${i}-${item}`}>
                        <span className="sections__marker" aria-hidden="true" />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="sections__text">{field.text}</p>
                )}
              </div>
            );
          })}
        </section>
      ))}
    </article>
  );
}
