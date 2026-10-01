import type { RouteObject } from 'react-router';
import { GuardaSessao } from './GuardaSessao';
import { Estrutura } from '../componentes/Estrutura';
import { EstadoVazio } from '../componentes/EstadoVazio';
import { TelaAlunos } from '../paginas/TelaAlunos';
import { TelaAprovado } from '../paginas/TelaAprovado';
import { TelaConferencia } from '../paginas/TelaConferencia';
import { TelaConferenciaSemestral } from '../paginas/TelaConferenciaSemestral';
import { TelaEntrar } from '../paginas/TelaEntrar';
import { TelaGravacao } from '../paginas/TelaGravacao';
import { TelaPainel } from '../paginas/TelaPainel';
import { TelaPerguntas } from '../paginas/TelaPerguntas';
import { TelaProcessando } from '../paginas/TelaProcessando';
import { TelaRelatorioEmAberto } from '../paginas/TelaRelatorioEmAberto';
import { TelaSemestral } from '../paginas/TelaSemestral';

/** Página provisória para as rotas ainda não implementadas. */
function PaginaProvisoria({ titulo }: { titulo: string }) {
  return (
    <Estrutura>
      <div className="pagina pagina--estreita">
        <EstadoVazio icone="construction" texto={titulo} />
      </div>
    </Estrutura>
  );
}

function protegida(elemento: React.ReactNode) {
  return <GuardaSessao>{elemento}</GuardaSessao>;
}

/** Rotas do portal (contracts/rotas-portal.md): uma rota por tela do protótipo (01 a 12). */
export const rotas: RouteObject[] = [
  { path: '/entrar', element: <TelaEntrar /> },
  { path: '/', element: protegida(<TelaPainel />) },
  { path: '/alunos', element: protegida(<TelaAlunos />) },
  { path: '/alunos/:alunoId/relatorio-em-aberto', element: protegida(<TelaRelatorioEmAberto />) },
  { path: '/alunos/:alunoId/nova-aula', element: protegida(<TelaGravacao />) },
  { path: '/trabalhos/:trabalhoId', element: protegida(<TelaProcessando />) },
  { path: '/relatorios-aula/:relatorioId/perguntas', element: protegida(<TelaPerguntas />) },
  { path: '/relatorios-aula/:relatorioId', element: protegida(<TelaConferencia />) },
  { path: '/relatorios-aula/:relatorioId/aprovado', element: protegida(<TelaAprovado />) },
  { path: '/alunos/:alunoId/semestral', element: protegida(<TelaSemestral />) },
  { path: '/relatorios-semestrais/:relatorioId', element: protegida(<TelaConferenciaSemestral />) },
  { path: '/relatorios-semestrais/:relatorioId/aprovado', element: protegida(<TelaAprovado semestral />) },
  { path: '/alunos/:alunoId/historico', element: protegida(<PaginaProvisoria titulo="12 Histórico" />) },
  { path: '*', element: protegida(<PaginaProvisoria titulo="Página não encontrada" />) },
];
