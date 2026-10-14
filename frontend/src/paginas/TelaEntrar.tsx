import { useEffect, useId, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { ErroApi } from '../servicos/clienteApi';
import { useEntrar } from '../servicos/sessao';

type Situacao =
  | { tipo: 'normal' }
  | { tipo: 'invalido'; mensagem: string }
  | { tipo: 'bloqueado'; ate: number }
  | { tipo: 'sem-conexao' }
  | { tipo: 'erro'; mensagem: string };

function destinoSeguro(destino: string | null): string {
  return destino && destino.startsWith('/') && !destino.startsWith('//') ? destino : '/';
}

/** Tela 01 — Entrar (e variações 01b código inválido, 01c bloqueio, 01d sem conexão). */
export function TelaEntrar() {
  const [parametros] = useSearchParams();
  const navegar = useNavigate();
  const entrar = useEntrar();
  const [codigo, setCodigo] = useState('');
  const [situacao, setSituacao] = useState<Situacao>({ tipo: 'normal' });
  const [agora, setAgora] = useState(() => Date.now());
  const idMensagem = useId();
  const motivo = parametros.get('motivo');

  const bloqueado = situacao.tipo === 'bloqueado' && situacao.ate > agora;
  const segundosRestantes = situacao.tipo === 'bloqueado' ? Math.max(0, situacao.ate - agora) / 1000 : 0;

  useEffect(() => {
    if (situacao.tipo !== 'bloqueado') return;
    const relogio = window.setInterval(() => setAgora(Date.now()), 1000);
    return () => window.clearInterval(relogio);
  }, [situacao]);

  const enviar = (evento: FormEvent) => {
    evento.preventDefault();
    if (bloqueado || entrar.isPending) return;
    entrar.mutate(codigo.trim(), {
      onSuccess: () => navegar(destinoSeguro(parametros.get('destino')), { replace: true }),
      onError: (erro) => {
        if (erro instanceof ErroApi && erro.status === 429) {
          const segundos = erro.retryAfterSeconds ?? 15 * 60;
          setAgora(Date.now());
          setSituacao({ tipo: 'bloqueado', ate: Date.now() + segundos * 1000 });
        } else if (erro instanceof ErroApi && erro.status === 0) {
          setSituacao({ tipo: 'sem-conexao' });
        } else if (erro instanceof ErroApi && erro.status < 500) {
          setSituacao({ tipo: 'invalido', mensagem: erro.message });
        } else {
          setSituacao({ tipo: 'erro', mensagem: msg('INTERNAL_ERROR') });
        }
      },
    });
  };

  const invalido = situacao.tipo === 'invalido';

  return (
    <div className="app">
      <main id="conteudo" className="app__principal">
        <div className="entrar">
          <div className="entrar__marca">
            <img src="/marca/fb-logo-metal.png" alt="Instituto Musical Felipe Blaft" />
          </div>
          <div className="entrar__lado-formulario">
            <form className="entrar__formulario" onSubmit={enviar} noValidate>
              <div>
                <div className="sobretitulo">Portal do Professor</div>
                <h1 className="titulo-pagina entrar__titulo">Entrar</h1>
                <p className="subtitulo">
                  Informe o mesmo código de vinculação que você usa no Telegram para acessar seus alunos e
                  relatórios.
                </p>
              </div>
              {motivo === 'SESSION_EXPIRED' && situacao.tipo === 'normal' ? (
                <div role="status" className="alerta alerta--info">
                  <Icone nome="info" />
                  <div className="alerta__texto">{msg('SESSION_EXPIRED')}</div>
                </div>
              ) : null}
              {bloqueado ? (
                <div role="alert" className="alerta alerta--aviso">
                  <Icone nome="lock_clock" />
                  <div className="alerta__texto">
                    {msg('TEMPORARILY_BLOCKED', {
                      minutes: Math.max(1, Math.ceil(segundosRestantes / 60)),
                    })}
                  </div>
                </div>
              ) : null}
              {situacao.tipo === 'sem-conexao' ? (
                <div role="alert" className="alerta alerta--erro">
                  <Icone nome="wifi_off" />
                  <div className="alerta__texto">{msg('NO_CONNECTION')}</div>
                </div>
              ) : null}
              {situacao.tipo === 'erro' ? (
                <div role="alert" className="alerta alerta--erro">
                  <Icone nome="error" />
                  <div className="alerta__texto">{situacao.mensagem}</div>
                </div>
              ) : null}
              <div className="campo">
                <label htmlFor="codigo" className="campo__rotulo">
                  Código de acesso
                </label>
                <input
                  id="codigo"
                  className="entrada entrada--codigo"
                  value={codigo}
                  onChange={(evento) => {
                    setCodigo(evento.target.value.toUpperCase());
                    if (invalido) setSituacao({ tipo: 'normal' });
                  }}
                  disabled={bloqueado}
                  autoComplete="one-time-code"
                  autoCapitalize="characters"
                  spellCheck={false}
                  maxLength={16}
                  placeholder="K7M2-9QXT"
                  aria-invalid={invalido}
                  aria-describedby={idMensagem}
                />
                {invalido ? (
                  <div id={idMensagem} className="campo__erro" role="alert">
                    <Icone nome="error" tamanho="pequeno" />
                    {situacao.mensagem}
                  </div>
                ) : (
                  <div id={idMensagem} className="campo__ajuda">
                    O código tem 8 caracteres, letras e números.
                  </div>
                )}
              </div>
              <Botao
                type="submit"
                tamanho="grande"
                disabled={bloqueado || codigo.trim().length === 0}
                carregando={entrar.isPending}
              >
                {entrar.isPending ? 'Entrando…' : 'Entrar'}
              </Botao>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
