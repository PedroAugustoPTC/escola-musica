import { useQuery } from '@tanstack/react-query';
import { msg } from '../mensagens/mensagens';
import { api, type Esquemas } from './clienteApi';
import { formatarData } from './formatacao';
import type { AudioSelecionado, AprovacaoDto, TrabalhoRefDto } from './relatoriosAula';

export type SemestreDto = Esquemas['SemestreDto'];
export type PreviaSemestralDto = Esquemas['PreviaSemestralDto'];
export type RelatorioSemestralDto = Esquemas['RelatorioSemestralDto'];

export const chaveRelatorioSemestral = (id: string | undefined) => ['relatorio-semestral', id] as const;

export function useSemestres(alunoId: string | undefined) {
  return useQuery<SemestreDto[]>({
    queryKey: ['semestres', alunoId],
    queryFn: ({ signal }) => api.get<SemestreDto[]>(`/alunos/${alunoId}/semestres`, signal),
    enabled: Boolean(alunoId),
  });
}

export function usePreviaSemestral(alunoId: string | undefined, periodoChave: string | null) {
  return useQuery<PreviaSemestralDto>({
    queryKey: ['previa-semestral', alunoId, periodoChave],
    queryFn: ({ signal }) =>
      api.get<PreviaSemestralDto>(`/alunos/${alunoId}/semestres/${periodoChave}/previa`, signal),
    enabled: Boolean(alunoId && periodoChave),
    gcTime: 0,
  });
}

export function useRelatorioSemestral(id: string | undefined) {
  return useQuery<RelatorioSemestralDto>({
    queryKey: chaveRelatorioSemestral(id),
    queryFn: ({ signal }) => api.get<RelatorioSemestralDto>(`/relatorios-semestrais/${id}`, signal),
    enabled: Boolean(id),
  });
}

function formularioAudio(audio: AudioSelecionado, versaoBase: number): FormData {
  const formulario = new FormData();
  formulario.append('audio', audio.arquivo, audio.nome);
  formulario.append('versaoBase', String(versaoBase));
  return formulario;
}

export const semestral = {
  obter: (id: string) => api.get<RelatorioSemestralDto>(`/relatorios-semestrais/${id}`),
  gerar: (alunoId: string, periodoChave: string, confirmarMesmoComEmAberto: boolean) =>
    api.post<TrabalhoRefDto>(`/alunos/${alunoId}/relatorios-semestrais`, {
      periodoChave,
      confirmarMesmoComEmAberto,
    }),
  alterarTexto: (id: string, texto: string, versaoBase: number) =>
    api.post<TrabalhoRefDto>(`/relatorios-semestrais/${id}/alteracoes`, { texto, versaoBase }),
  alterarAudio: (id: string, audio: AudioSelecionado, versaoBase: number) =>
    api.postFormulario<TrabalhoRefDto>(
      `/relatorios-semestrais/${id}/alteracoes`,
      formularioAudio(audio, versaoBase),
    ),
  aprovar: (id: string, versaoConfirmada: number) =>
    api.post<AprovacaoDto>(`/relatorios-semestrais/${id}/aprovacao`, { versaoConfirmada }),
  cancelar: (id: string) => api.post<void>(`/relatorios-semestrais/${id}/cancelamento`),
  descartarConsolidacao: (trabalhoId: string) => api.post<void>(`/trabalhos/${trabalhoId}/descarte`),
};

export function caminhoPdfSemestral(id: string, versao: number): string {
  return `/relatorios-semestrais/${id}/versoes/${versao}/pdf`;
}

/** Campo de uma seção do semestral: itens em lista ou texto (vazio mostra "—"). */
export interface CampoSemestral {
  rotulo: string | null;
  itens: string[];
  texto: string;
}

export interface SecaoSemestral {
  numero: number;
  titulo: string;
  campos: CampoSemestral[];
}

type Conteudo = RelatorioSemestralDto['conteudo'];
type Info = RelatorioSemestralDto['informacoesGerais'];
type Objeto = Record<string, unknown>;

function texto(rotulo: string, valor: unknown): CampoSemestral {
  return { rotulo: msg(rotulo), itens: [], texto: typeof valor === 'string' ? valor : '' };
}

function lista(rotulo: string | null, valor: unknown): CampoSemestral {
  return {
    rotulo: rotulo ? msg(rotulo) : null,
    itens: Array.isArray(valor) ? valor.filter((i): i is string => typeof i === 'string') : [],
    texto: '',
  };
}

const parte = (conteudo: Conteudo, chave: string): Objeto =>
  ((conteudo as unknown as Objeto)[chave] as Objeto | undefined) ?? {};

/** As 10 seções do semestral (data-model.md §9) com os rótulos do catálogo único. */
export function secoesSemestral(info: Info, conteudo: Conteudo): SecaoSemestral[] {
  const f = parte(conteudo, 'frequenciaEEstudo');
  const t = parte(conteudo, 'tecnica');
  const m = parte(conteudo, 'musicalidade');
  const l = parte(conteudo, 'leituraEMemorizacao');
  const a = parte(conteudo, 'acompanhamentoFamiliar');
  const p = parte(conteudo, 'planejamentoProximoSemestre');
  return [
    {
      numero: 1,
      titulo: msg('SECAO_INFORMACOES_GERAIS'),
      campos: [
        texto('INFO_NOME', info.nome),
        texto('INFO_IDADE', msg('INFO_IDADE_VALOR', { idade: info.idade })),
        texto('INFO_INSTRUMENTO', info.instrumento ?? ''),
        texto('INFO_PROFESSOR', info.professor),
        texto(
          'INFO_PERIODO',
          `${info.periodoRotulo} (${formatarData(info.periodoInicio)} a ${formatarData(info.periodoFim)})`,
        ),
        texto('INFO_AULAS_CONSIDERADAS', String(info.quantidadeAulasConsideradas)),
      ],
    },
    {
      numero: 2,
      titulo: msg('SECAO_FREQUENCIA_E_ESTUDO'),
      campos: [
        texto('CAMPO_FREQUENCIA_AULAS', f.frequenciaAulas),
        texto('CAMPO_REGULARIDADE_ESTUDO_EM_CASA', f.regularidadeEstudoEmCasa),
        texto('CAMPO_ENGAJAMENTO', f.engajamento),
        texto('CAMPO_COMENTARIO_GERAL', f.comentarioGeral),
      ],
    },
    {
      numero: 3,
      titulo: msg('SECAO_TECNICA'),
      campos: [
        lista('CAMPO_CONTEUDOS_TRABALHADOS', t.conteudosTrabalhados),
        texto('CAMPO_EVOLUCAO_TECNICA', t.evolucaoTecnica),
        lista('CAMPO_PONTOS_A_DESENVOLVER', t.pontosADesenvolver),
        lista('CAMPO_ESTUDOS_REALIZADOS', t.estudosRealizados),
      ],
    },
    {
      numero: 4,
      titulo: msg('SECAO_MUSICALIDADE'),
      campos: [
        lista('CAMPO_REPERTORIO_ESTUDADO', m.repertorioEstudado),
        texto('CAMPO_SOM', m.som),
        texto('CAMPO_RITMO', m.ritmo),
        texto('CAMPO_FRASEADO', m.fraseado),
        texto('CAMPO_ARTICULACAO', m.articulacao),
        texto('CAMPO_ESTILO', m.estilo),
        texto('CAMPO_COMENTARIO_MUSICAL', m.comentarioMusical),
      ],
    },
    {
      numero: 5,
      titulo: msg('SECAO_LEITURA_E_MEMORIZACAO'),
      campos: [texto('CAMPO_AVALIACAO', l.avaliacao), texto('CAMPO_COMENTARIO', l.comentario)],
    },
    {
      numero: 6,
      titulo: msg('SECAO_PONTOS_DE_ATENCAO'),
      campos: [lista(null, parte(conteudo, 'pontosDeAtencao').itens)],
    },
    {
      numero: 7,
      titulo: msg('SECAO_ESTRATEGIAS_PEDAGOGICAS'),
      campos: [lista(null, parte(conteudo, 'estrategiasPedagogicas').itens)],
    },
    {
      numero: 8,
      titulo: msg('SECAO_ACOMPANHAMENTO_FAMILIAR'),
      campos: [
        texto('CAMPO_NIVEL_DE_PRESENCA', a.nivelDePresenca),
        texto('CAMPO_OBSERVACAO_DO_PROFESSOR', a.observacaoDoProfessor),
      ],
    },
    {
      numero: 9,
      titulo: msg('SECAO_PLANEJAMENTO_PROXIMO_SEMESTRE'),
      campos: [
        lista('CAMPO_METAS_TECNICAS', p.metasTecnicas),
        lista('CAMPO_METAS_MUSICAIS', p.metasMusicais),
        lista('CAMPO_HABITOS_DE_ESTUDO', p.habitosDeEstudo),
        lista('CAMPO_REPERTORIO_PROPOSTO', p.repertorioProposto),
        lista('CAMPO_ESTUDOS_TECNICOS', p.estudosTecnicos),
        lista('CAMPO_LEITURA_E_TEORIA', p.leituraETeoria),
        texto('CAMPO_PROJETO_DO_SEMESTRE', p.projetoDoSemestre),
      ],
    },
    {
      numero: 10,
      titulo: msg('SECAO_PARECER_FINAL'),
      campos: [
        {
          rotulo: null,
          itens: [],
          texto: typeof conteudo.parecerFinal === 'string' ? conteudo.parecerFinal : '',
        },
      ],
    },
  ];
}
