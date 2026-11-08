package br.com.felipeblaft.reports.domain.telegram;

import br.com.felipeblaft.reports.domain.privacy.ConsentInput;

/**
 * Consentimento do responsável em coleta no cadastro guiado do Telegram (contrato {@code
 * telegram-consent.md}, passos C1 a C5). Guarda só texto já normalizado; nome de quem consentiu
 * nunca aparece no {@code toString}, no resumo nem em log. {@code termVersion} preenchido registra
 * que o professor respondeu "sim" ao passo C1 diante da versão vigente do termo.
 *
 * @param termVersion versão do termo apresentada no C1 (fixada no rascunho)
 * @param date {@code aaaa-mm-dd}, já validada e não futura
 */
public record ConsentDraft(
        Integer termVersion, String name, String relationship, String date, String method) {

    public static final ConsentDraft EMPTY = new ConsentDraft(null, null, null, null, null);

    /** Passos de C1 a C5, na ordem em que o bot pergunta. */
    public enum Part {
        ACKNOWLEDGE,
        NAME,
        RELATIONSHIP,
        DATE,
        METHOD
    }

    /** Próximo passo a perguntar, ou {@code null} quando o consentimento está completo. */
    public Part nextPart() {
        if (termVersion == null) {
            return Part.ACKNOWLEDGE;
        }
        if (name == null) {
            return Part.NAME;
        }
        if (relationship == null) {
            return Part.RELATIONSHIP;
        }
        if (date == null) {
            return Part.DATE;
        }
        return method == null ? Part.METHOD : null;
    }

    public ConsentDraft withTermVersion(int version) {
        // outra versão do termo recomeça o consentimento: o que foi informado valia para a anterior
        return new ConsentDraft(version, null, null, null, null);
    }

    public ConsentDraft withName(String value) {
        return new ConsentDraft(termVersion, value, relationship, date, method);
    }

    public ConsentDraft withRelationship(String value) {
        return new ConsentDraft(termVersion, name, value, date, method);
    }

    public ConsentDraft withDate(String value) {
        return new ConsentDraft(termVersion, name, relationship, value, method);
    }

    public ConsentDraft withMethod(String value) {
        return new ConsentDraft(termVersion, name, relationship, date, value);
    }

    /** Entrada completa para o caso de uso, ou {@code null} enquanto faltar algum passo. */
    public ConsentInput toInput() {
        return nextPart() == null
                ? new ConsentInput(name, relationship, date, termVersion, method)
                : null;
    }

    @Override
    public String toString() {
        return "ConsentDraft";
    }
}
