import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button } from './Button';
import { Icon } from './Icon';

interface DialogProps {
  open: boolean;
  title: string;
  text: ReactNode;
  icon: string;
  danger?: boolean;
  confirmLabel: string;
  backLabel?: string;
  loading?: boolean;
  error?: ReactNode;
  onConfirm: () => void;
  onClose: () => void;
}

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Diálogo de confirmação (08e, 08f, 04b): foco preso dentro do diálogo, `Esc` fecha, foco volta ao
 * elemento que o abriu. A confirmação só avança com a resposta 2xx (quem chama controla).
 */
export function ConfirmationDialog({
  open,
  title,
  text,
  icon,
  danger = false,
  confirmLabel,
  backLabel = 'Voltar',
  loading = false,
  error,
  onConfirm,
  onClose,
}: DialogProps) {
  const titleId = useId();
  const textId = useId();
  const box = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const first = box.current?.querySelector<HTMLElement>(FOCUSABLE);
    first?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab' || !box.current) return;
      const focusable = Array.from(box.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (focusable.length === 0) return;
      const start = focusable[0]!;
      const end = focusable[focusable.length - 1]!;
      if (event.shiftKey && document.activeElement === start) {
        event.preventDefault();
        end.focus();
      } else if (!event.shiftKey && document.activeElement === end) {
        event.preventDefault();
        start.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previous?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div className="dialog-backdrop">
      <div
        ref={box}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={textId}
        className="dialog"
      >
        <span
          className={`icon-circle dialog__icon ${danger ? 'icon-circle--error' : 'icon-circle--neutral'}`}
        >
          <Icon name={icon} />
        </span>
        <h2 id={titleId} className="dialog__title">
          {title}
        </h2>
        <p id={textId} className="dialog__text">
          {text}
        </p>
        {error ? (
          <div role="alert" className="alert alert--error">
            <Icon name="error" />
            <div className="alert__text">{error}</div>
          </div>
        ) : null}
        <div className="dialog__actions">
          <Button
            variant={danger ? 'filled-danger' : 'primary'}
            size="large"
            loading={loading}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            {backLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
