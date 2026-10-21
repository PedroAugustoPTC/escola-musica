/** Tipo do segredo exibido uma única vez: código de acesso do professor ou de ativação do administrador. */
export type OneTimeSecretKind = 'access-code' | 'activation-code';

/**
 * Propriedades do painel de código exibido uma única vez (contracts/portal-screens.md §3).
 * O valor vive só em estado do componente; nunca em cache de consulta, armazenamento, URL ou título.
 */
export interface OneTimeSecretPanelProps {
  kind: OneTimeSecretKind;
  /** O código em claro; apagado da memória quando o usuário confirma que anotou. */
  secret: string;
  /** Nome do destinatário (professor ou administrador). */
  recipientName: string;
  /** Validade do código, ISO 8601. */
  validUntil: string;
  /** "Já anotei o código": o chamador descarta o valor e libera a navegação. */
  onAcknowledge: () => void;
}
