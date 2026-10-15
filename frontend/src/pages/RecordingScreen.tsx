import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { LoadingState } from '../components/LoadingState';
import { ErrorAlert, ErrorState } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { AudioRecorder } from '../components/AudioRecorder';
import { msg } from '../messages/messages';
import { useStudent } from '../services/students';
import { ApiError } from '../services/apiClient';
import { formatDate, todayAtSchool } from '../services/formatting';
import { useHome } from '../services/home';
import { lessonReports, type SelectedAudio } from '../services/lessonReports';

const DEFAULT_LIMIT_MB = 50;

/** Tela 05 — Gravação (05b gravando, 05c concluída, 05d só arquivo, 05e modo escuro). */
export function RecordingScreen() {
  const { studentId } = useParams();
  const student = useStudent(studentId);
  const start = useHome();
  const navigate = useNavigate();
  const [audio, setAudio] = useState<SelectedAudio | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const limitMb = start.data?.audioLimitMb ?? DEFAULT_LIMIT_MB;

  const context = {
    kicker: 'Novo relatório',
    title: student.data?.name ?? '',
    backTo: '/students?register=1',
  };

  if (student.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Carregando o aluno" />
      </Layout>
    );
  }
  if (student.isError) {
    return (
      <Layout activeNav="students" context={context}>
        <ErrorState error={student.error} />
      </Layout>
    );
  }

  const send = async () => {
    if (!audio || !studentId) return;
    setUploading(true);
    setError(null);
    try {
      const { jobId } = await lessonReports.register(studentId, audio);
      navigate(`/jobs/${jobId}`, { state: { studentId } });
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === 'REPORT_ALREADY_OPEN') {
        // FR-034: o aluno já tem relatório em aberto; a tela 04 oferece continuar ou descartar
        navigate(`/students/${studentId}/open-report`, { replace: true });
        return;
      }
      setError(failure);
      setUploading(false);
    }
  };

  return (
    <Layout
      activeNav="students"
      context={context}
      narrowBar
      actionsBar={
        <Button
          size="large"
          block
          icon="send"
          disabled={!audio}
          loading={uploading}
          onClick={() => void send()}
        >
          Enviar áudio e gerar relatório
        </Button>
      }
    >
      <div className="page page--narrow">
        <div>
          <div className="kicker">Novo relatório de aula</div>
          <h1 className="page-title">{student.data.name}</h1>
          <p className="subtitle">Aula de {formatDate(todayAtSchool())}</p>
        </div>
        {error ? <ErrorAlert error={error} /> : null}
        <AudioRecorder
          limitMb={limitMb}
          onChange={setAudio}
          disabled={uploading}
          tip="Conte o que foi trabalhado, como o aluno evoluiu, as dificuldades e o que ele deve estudar em casa."
        />
        <p className="support-text">{msg('FORMATS_AND_LIMIT', { limitMb })}</p>
      </div>
    </Layout>
  );
}
