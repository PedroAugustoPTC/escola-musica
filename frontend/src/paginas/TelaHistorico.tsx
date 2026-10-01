import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useEhMovel } from '../app/useEhMovel';
import { Botao } from '../componentes/Botao';
import { EstadoCarregando } from '../componentes/EstadoCarregando';
import { EstadoErro } from '../componentes/EstadoErro';
import { EstadoVazio } from '../componentes/EstadoVazio';
import { Estrutura } from '../componentes/Estrutura';
import { Icone } from '../componentes/Icone';
import { msg } from '../mensagens/mensagens';
import {
  pdfOficial,
  seloDoItem,
  textoAprovadoEm,
  useHistorico,
  type ItemHistoricoDto,
} from '../servicos/historico';
import { abrirPdf } from '../servicos/pdf';

/**
 * Tela 12 — Histórico: relatórios aprovados do aluno, do mais recente ao mais antigo, com os mesmos
 * rótulos do bot; "Abrir PDF" abre o documento oficial numa nova aba (12b vazio, 12c carregando).
 */
export function TelaHistorico() {
  const { alunoId } = useParams();
  const navegar = useNavigate();
  const movel = useEhMovel();
  const historico = useHistorico(alunoId);
  const [abrindo, setAbrindo] = useState<string | null>(null);
  const [aviso, setAviso] = useState<{ tipo: 'info' | 'erro'; texto: string } | null>(null);

  const contexto = {
    sobretitulo: 'Histórico',
    titulo: historico.data?.aluno.nome ?? '',
    voltarPara: '/alunos',
  };

  if (historico.isPending) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto} navInferior>
        <EstadoCarregando rotulo="Carregando histórico" />
      </Estrutura>
    );
  }
  if (historico.isError) {
    return (
      <Estrutura navAtiva="alunos" contexto={contexto} navInferior>
        <EstadoErro erro={historico.error} aoTentarDeNovo={() => void historico.refetch()} />
      </Estrutura>
    );
  }

  const { aluno, itens } = historico.data;

  const abrir = async (item: ItemHistoricoDto) => {
    const { caminho, nomeArquivo } = pdfOficial(item);
    setAviso(null);
    setAbrindo(item.relatorioId);
    try {
      const abriu = await abrirPdf(caminho, nomeArquivo);
      if (!abriu) setAviso({ tipo: 'info', texto: msg('PDF_BLOQUEADO_NAVEGADOR') });
    } catch (falha) {
      setAviso({ tipo: 'erro', texto: falha instanceof Error ? falha.message : msg('ERRO_INTERNO') });
    } finally {
      setAbrindo(null);
    }
  };

  return (
    <Estrutura navAtiva="alunos" contexto={contexto} navInferior>
      <div className="pagina pagina--media">
        <div>
          <div className="sobretitulo">Histórico</div>
          <h1 className="titulo-pagina">{aluno.nome}</h1>
          <p className="subtitulo">Relatórios aprovados, do mais recente ao mais antigo.</p>
        </div>
        {aviso ? (
          <div role={aviso.tipo === 'erro' ? 'alert' : 'status'} className={`alerta alerta--${aviso.tipo}`}>
            <div className="alerta__texto">{aviso.texto}</div>
          </div>
        ) : null}
        {itens.length === 0 ? (
          <EstadoVazio
            monograma
            texto={msg('HISTORICO_VAZIO')}
            acao={
              <Botao onClick={() => navegar(`/alunos/${aluno.id}/nova-aula`)}>
                {msg('BOTAO_REGISTRAR_AULA')}
              </Botao>
            }
          />
        ) : (
          <ol className="historico" aria-label={msg('HISTORICO_TITULO', { aluno: aluno.nome })}>
            {itens.map((item) => {
              const selo = seloDoItem(item);
              return (
                <li key={`${item.tipo}-${item.relatorioId}`} className="historico__item">
                  <div className="historico__selo" aria-hidden="true">
                    <div className="historico__dia">{selo.dia}</div>
                    <div className="historico__mes">{selo.mes}</div>
                  </div>
                  <div className="historico__corpo">
                    <div className="historico__titulo">
                      <span>{item.rotulo}</span>
                      {item.tipo === 'SEMESTRAL' ? (
                        <span className="historico__etiqueta">Semestral</span>
                      ) : null}
                    </div>
                    <div className="historico__aprovado">
                      <Icone nome="check_circle" />
                      {textoAprovadoEm(item)}
                    </div>
                  </div>
                  <Botao
                    variante="secundario"
                    icone="picture_as_pdf"
                    className="historico__pdf"
                    aria-label={`${msg('BOTAO_ABRIR_PDF')} — ${item.rotulo}`}
                    carregando={abrindo === item.relatorioId}
                    onClick={() => void abrir(item)}
                  >
                    {movel ? 'PDF' : msg('BOTAO_ABRIR_PDF')}
                  </Botao>
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </Estrutura>
  );
}
