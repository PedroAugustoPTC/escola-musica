import { ErroApi } from '../servicos/clienteApi';
import { msg } from '../mensagens/mensagens';
import { Botao } from './Botao';
import { Icone } from './Icone';

/** Texto do catálogo para qualquer erro (nunca detalhe interno, FR-054). */
export function textoDoErro(erro: unknown): string {
  if (erro instanceof ErroApi) return erro.message;
  return msg('ERRO_INTERNO');
}

/** Alerta de erro dentro de uma tela, com "Tentar novamente" opcional. */
export function AlertaErro({ erro, aoTentarDeNovo }: { erro: unknown; aoTentarDeNovo?: () => void }) {
  const offline = erro instanceof ErroApi && erro.status === 0;
  return (
    <div role="alert" className="alerta alerta--erro">
      <Icone nome={offline ? 'wifi_off' : 'error'} />
      <div className="alerta__texto">{textoDoErro(erro)}</div>
      {aoTentarDeNovo ? (
        <Botao variante="perigo" tamanho="compacto" onClick={aoTentarDeNovo}>
          Tentar novamente
        </Botao>
      ) : null}
    </div>
  );
}

/** Estado de erro de página inteira. */
export function EstadoErro({ erro, aoTentarDeNovo }: { erro: unknown; aoTentarDeNovo?: () => void }) {
  return (
    <div className="pagina pagina--estreita">
      <AlertaErro erro={erro} aoTentarDeNovo={aoTentarDeNovo} />
    </div>
  );
}
