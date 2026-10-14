import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Botao } from '../componentes/Botao';
import { CartaoAluno } from '../componentes/CartaoAluno';
import { EsqueletoCartoes } from '../componentes/EstadoCarregando';
import { AlertaErro } from '../componentes/EstadoErro';
import { EstadoVazio } from '../componentes/EstadoVazio';
import { Estrutura } from '../componentes/Estrutura';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import { useAlunos } from '../servicos/alunos';

const ATRASO_BUSCA_MS = 300;

/** Tela 03 — Alunos (03b busca sem resultado, 03c carregando; "Para qual aluno?" ao registrar). */
export function TelaAlunos() {
  const [parametros, setParametros] = useSearchParams();
  const registrando = parametros.get('registrar') === '1';
  const [busca, setBusca] = useState(parametros.get('busca') ?? '');
  const [termo, setTermo] = useState(busca);
  const campo = useRef<HTMLInputElement>(null);
  const alunos = useAlunos(termo);

  useEffect(() => {
    const espera = window.setTimeout(() => setTermo(busca), ATRASO_BUSCA_MS);
    return () => window.clearTimeout(espera);
  }, [busca]);

  useEffect(() => {
    const novos = new URLSearchParams(parametros);
    if (termo.trim()) novos.set('busca', termo.trim());
    else novos.delete('busca');
    novos.delete('foco');
    if (novos.toString() !== parametros.toString()) setParametros(novos, { replace: true });
  }, [termo, parametros, setParametros]);

  useEffect(() => {
    if (parametros.get('foco') === 'busca') campo.current?.focus();
  }, [parametros]);

  const dados = alunos.data;
  const total = dados?.result === 'FOUND' ? null : dados?.students.length;

  return (
    <Estrutura navAtiva={registrando ? 'registrar' : 'alunos'} navInferior>
      <div className="pagina pagina--larga">
        <div>
          <div className="sobretitulo">{registrando ? msg('REGISTER_LESSON_BUTTON') : 'Alunos'}</div>
          <h1 className="titulo-pagina">{registrando ? msg('CHOOSE_STUDENT') : 'Seus alunos'}</h1>
          {total !== null && total !== undefined ? (
            <p className="subtitulo">{total} aluno(s) associado(s) a você.</p>
          ) : null}
        </div>
        <label className="busca">
          <span className="oculto-visual">Buscar aluno por nome</span>
          <Icone nome="search" />
          <input
            ref={campo}
            type="search"
            className="entrada"
            value={busca}
            placeholder="Buscar aluno por nome"
            onChange={(evento) => setBusca(evento.target.value)}
          />
        </label>
        {alunos.isPending ? <EsqueletoCartoes rotulo="Carregando alunos" /> : null}
        {alunos.isError ? <AlertaErro erro={alunos.error} aoTentarDeNovo={() => alunos.refetch()} /> : null}
        {dados && dados.students.length === 0 ? (
          <EstadoVazio monograma solido titulo="Nenhum aluno por aqui" texto={msg('NO_STUDENTS')} />
        ) : null}
        {dados?.result === 'NONE' && dados.students.length > 0 ? (
          <EstadoVazio
            icone="person_search"
            texto={msg('NO_STUDENT_FOUND', { search: termo.trim() })}
            acao={
              <Botao variante="secundario" tamanho="compacto" onClick={() => setBusca('')}>
                Limpar busca
              </Botao>
            }
          />
        ) : null}
        {dados && dados.students.length > 0 ? (
          <div className="grade-cartoes" aria-busy={alunos.isFetching || undefined}>
            {dados.students.map((aluno) => (
              <CartaoAluno key={aluno.id} aluno={aluno} />
            ))}
          </div>
        ) : null}
      </div>
    </Estrutura>
  );
}
