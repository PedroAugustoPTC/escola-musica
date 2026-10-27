import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { OneTimeSecretPanel } from '../../components/admin/OneTimeSecretPanel';
import { TeacherForm } from '../../components/admin/TeacherForm';
import { useRegisterTeacher, type TeacherWithCode } from '../../services/admin/teachers';

/**
 * A06 — Cadastrar professor; depois do cadastro, A07 — código de acesso exibido uma única vez.
 * O código fica só em estado do componente e é descartado ao confirmar que foi anotado.
 */
export function TeacherFormScreen() {
  const navigate = useNavigate();
  const register = useRegisterTeacher();
  const [issued, setIssued] = useState<TeacherWithCode | null>(null);

  if (issued) {
    return (
      <div className="page page--medium">
        <div>
          <div className="kicker">Cadastros</div>
          <h1 className="page-title">Professor cadastrado</h1>
          <p className="subtitle">Entregue o código ao professor por um canal de confiança.</p>
        </div>
        <OneTimeSecretPanel
          kind="access-code"
          secret={issued.accessCode}
          recipientName={issued.teacher.name}
          validUntil={issued.validUntil}
          onAcknowledge={() => {
            const teacherId = issued.teacher.id;
            setIssued(null);
            navigate(`/admin/teachers/${teacherId}`);
          }}
        />
      </div>
    );
  }

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Cadastros</div>
        <h1 className="page-title">Cadastrar professor</h1>
      </div>
      <TeacherForm
        submitLabel="Cadastrar"
        submitting={register.isPending}
        error={register.error}
        onSubmit={(values) =>
          register.mutate(values, {
            onSuccess: (result) => {
              setIssued(result);
              register.reset();
            },
          })
        }
        secondaryAction={
          <Link to="/admin/teachers" className="button button--secondary button--large">
            Cancelar
          </Link>
        }
      />
    </div>
  );
}
