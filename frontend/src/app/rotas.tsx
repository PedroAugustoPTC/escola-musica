import type { RouteObject } from 'react-router';
import { GuardaSessao } from './GuardaSessao';
import { Estrutura } from '../componentes/Estrutura';
import { EstadoVazio } from '../componentes/EstadoVazio';

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
  { path: '/entrar', element: <PaginaProvisoria titulo="01 Entrar" /> },
  { path: '/', element: protegida(<PaginaProvisoria titulo="02 Painel" />) },
  { path: '/alunos', element: protegida(<PaginaProvisoria titulo="03 Alunos" />) },
  {
    path: '/alunos/:alunoId/relatorio-em-aberto',
    element: protegida(<PaginaProvisoria titulo="04 Relatório em aberto" />),
  },
  { path: '/alunos/:alunoId/nova-aula', element: protegida(<PaginaProvisoria titulo="05 Gravação" />) },
  { path: '/trabalhos/:trabalhoId', element: protegida(<PaginaProvisoria titulo="06 Processando" />) },
  {
    path: '/relatorios-aula/:relatorioId/perguntas',
    element: protegida(<PaginaProvisoria titulo="07 Perguntas pendentes" />),
  },
  { path: '/relatorios-aula/:relatorioId', element: protegida(<PaginaProvisoria titulo="08 Conferência" />) },
  {
    path: '/relatorios-aula/:relatorioId/aprovado',
    element: protegida(<PaginaProvisoria titulo="09 Relatório aprovado" />),
  },
  { path: '/alunos/:alunoId/semestral', element: protegida(<PaginaProvisoria titulo="10 Semestral" />) },
  {
    path: '/relatorios-semestrais/:relatorioId',
    element: protegida(<PaginaProvisoria titulo="11 Conferência semestral" />),
  },
  {
    path: '/relatorios-semestrais/:relatorioId/aprovado',
    element: protegida(<PaginaProvisoria titulo="09 Semestral aprovado" />),
  },
  { path: '/alunos/:alunoId/historico', element: protegida(<PaginaProvisoria titulo="12 Histórico" />) },
  { path: '*', element: protegida(<PaginaProvisoria titulo="Página não encontrada" />) },
];
