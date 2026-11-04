import type { components } from '../../services/administration-api-types';
import type { StudentFormValues, StudentView } from './studentFormTypes';

type Schemas = components['schemas'];

/** Corpo do cadastro (`POST`): o CPF só trafega aqui, na entrada. */
export type StudentInputBody = Schemas['StudentInput'];
/** Corpo da edição (`PATCH`): campo ausente não muda; CPF em branco mantém o atual. */
export type StudentUpdateBody = Schemas['StudentUpdate'];

function consentOf(values: StudentFormValues) {
  const consentingName = values.consentingName.trim();
  const filled = consentingName || values.consentRelationship || values.consentedOn || values.consentMethod;
  if (!filled || values.consentTermVersion === null) return undefined;
  return {
    consentingName,
    relationship: values.consentRelationship as 'MOTHER' | 'FATHER' | 'LEGAL_GUARDIAN' | 'OTHER',
    consentedOn: values.consentedOn,
    termVersion: values.consentTermVersion,
    collectionMethod: values.consentMethod as 'SIGNED_PAPER' | 'DIGITAL' | 'OTHER',
  };
}

function guardianOf(values: StudentFormValues) {
  const name = values.guardianName.trim();
  const phone = values.guardianPhone.trim();
  const email = values.guardianEmail.trim();
  if (!name && !phone && !email) return undefined;
  return { name, phone, email };
}

/** Só mapeia o formulário para o corpo da API; as regras (CPF, data, responsável de menor) são do servidor. */
export function buildStudentInput(values: StudentFormValues): StudentInputBody {
  const instrument = values.instrument.trim();
  const guardian = guardianOf(values);
  const consent = consentOf(values);
  return {
    name: values.name.trim(),
    birthDate: values.birthDate,
    cpf: values.cpf.trim(),
    ...(instrument ? { instrument } : {}),
    ...(guardian ? { guardian } : {}),
    ...(consent ? { consent } : {}),
  };
}

/** Corpo da edição: o CPF só segue quando foi digitado; instrumento esvaziado limpa o atual. */
export function buildStudentUpdate(values: StudentFormValues, current?: StudentView): StudentUpdateBody {
  const instrument = values.instrument.trim();
  const guardian = guardianOf(values);
  const consent = consentOf(values);
  const cpf = values.cpf.trim();
  return {
    name: values.name.trim(),
    birthDate: values.birthDate,
    ...(cpf ? { cpf } : {}),
    ...(instrument || current?.instrument ? { instrument } : {}),
    ...(guardian ? { guardian } : {}),
    ...(consent ? { consent } : {}),
  };
}
