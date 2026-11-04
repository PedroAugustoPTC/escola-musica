import { useConsentTerms } from '../../services/admin/consents';
import { useCurrentConsentTerm } from '../../services/consent';
import type { StudentFormConsentTerm } from './studentFormTypes';

/**
 * Versão vigente do termo para a seção de consentimento do formulário de aluno. O professor lê a
 * versão e o contato da LGPD pela rota dele; o administrador lê a lista de versões e usa a mais
 * recente. Sem termo publicado, devolve `undefined` e a seção avisa.
 */
export function useTeacherConsentTerm(): StudentFormConsentTerm | undefined {
  const term = useCurrentConsentTerm().data;
  return term
    ? {
        number: term.number,
        publishedOn: term.publishedOn,
        text: term.text,
        privacyContact: { name: term.privacyContact.name, contact: term.privacyContact.contact },
      }
    : undefined;
}

export function useAdminConsentTerm(): StudentFormConsentTerm | undefined {
  const current = useConsentTerms().data?.[0];
  return current
    ? {
        number: current.number,
        publishedOn: current.publishedOn,
        text: current.text,
        privacyContact: { name: '', contact: '' },
      }
    : undefined;
}
