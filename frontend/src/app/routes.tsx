import type { RouteObject } from 'react-router';
import { SessionGuard } from './SessionGuard';
import { Layout } from '../components/Layout';
import { EmptyState } from '../components/EmptyState';
import { StudentsScreen } from '../pages/StudentsScreen';
import { ApprovedScreen } from '../pages/ApprovedScreen';
import { ReviewScreen } from '../pages/ReviewScreen';
import { SemesterReviewScreen } from '../pages/SemesterReviewScreen';
import { SignInScreen } from '../pages/SignInScreen';
import { HistoryScreen } from '../pages/HistoryScreen';
import { RecordingScreen } from '../pages/RecordingScreen';
import { DashboardScreen } from '../pages/DashboardScreen';
import { QuestionsScreen } from '../pages/QuestionsScreen';
import { ProcessingScreen } from '../pages/ProcessingScreen';
import { OpenReportScreen } from '../pages/OpenReportScreen';
import { SemesterScreen } from '../pages/SemesterScreen';
import { adminRoutes } from './adminRoutes';
import { myStudentsRoutes } from './myStudentsRoutes';

/** Página provisória para as rotas ainda não implementadas. */
function PlaceholderPage({ title }: { title: string }) {
  return (
    <Layout>
      <div className="page page--narrow">
        <EmptyState icon="construction" text={title} />
      </div>
    </Layout>
  );
}

function protectedRoute(element: React.ReactNode) {
  return <SessionGuard>{element}</SessionGuard>;
}

/** Rotas do portal (contracts/rotas-portal.md): uma rota por tela do protótipo (01 a 12). */
export const routes: RouteObject[] = [
  { path: '/sign-in', element: <SignInScreen /> },
  { path: '/', element: protectedRoute(<DashboardScreen />) },
  { path: '/students', element: protectedRoute(<StudentsScreen />) },
  { path: '/students/:studentId/open-report', element: protectedRoute(<OpenReportScreen />) },
  { path: '/students/:studentId/new-lesson', element: protectedRoute(<RecordingScreen />) },
  { path: '/jobs/:jobId', element: protectedRoute(<ProcessingScreen />) },
  { path: '/lesson-reports/:reportId/questions', element: protectedRoute(<QuestionsScreen />) },
  { path: '/lesson-reports/:reportId', element: protectedRoute(<ReviewScreen />) },
  { path: '/lesson-reports/:reportId/approved', element: protectedRoute(<ApprovedScreen />) },
  { path: '/students/:studentId/semester', element: protectedRoute(<SemesterScreen />) },
  { path: '/semester-reports/:reportId', element: protectedRoute(<SemesterReviewScreen />) },
  { path: '/semester-reports/:reportId/approved', element: protectedRoute(<ApprovedScreen semester />) },
  { path: '/students/:studentId/history', element: protectedRoute(<HistoryScreen />) },
  ...myStudentsRoutes,
  ...adminRoutes,
  { path: '*', element: protectedRoute(<PlaceholderPage title="Página não encontrada" />) },
];
