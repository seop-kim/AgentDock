import { useEffect, useState } from 'react';
import modal from '../styles/modal.module.css';
import styles from './ConfirmDialog.module.css';

interface ConfirmRequest {
  text: string;
  confirmLabel: string;
  resolve: (ok: boolean) => void;
}

let deliver: ((request: ConfirmRequest) => void) | null = null;

/**
 * 확인 창(`window.confirm` 대신). Promise<boolean> 로 답한다.
 * 앱에 `<ConfirmDialog />` 가 없으면 브라우저 기본 확인 창으로 떨어진다(동작이 막히지 않게).
 */
export function askConfirm(text: string, confirmLabel = '확인'): Promise<boolean> {
  return new Promise((resolve) => {
    if (deliver === null) {
      resolve(window.confirm(text));
      return;
    }
    deliver({ text, confirmLabel, resolve });
  });
}

/** 확인 창을 그리는 자리. `App` 이 한 번만 그린다. */
export default function ConfirmDialog() {
  const [request, setRequest] = useState<ConfirmRequest | null>(null);

  useEffect(() => {
    deliver = (next) => setRequest(next);
    return () => {
      deliver = null;
    };
  }, []);

  useEffect(() => {
    if (request === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        request.resolve(false);
        setRequest(null);
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [request]);

  if (request === null) return null;

  const answer = (ok: boolean) => {
    request.resolve(ok);
    setRequest(null);
  };

  return (
    <div className={modal.overlay} onClick={() => answer(false)}>
      <div
        className={`${modal.modal} ${styles.dialog}`}
        role="alertdialog"
        aria-label="확인"
        onClick={(e) => e.stopPropagation()}
      >
        <p className={styles.text}>{request.text}</p>
        <div className={styles.actions}>
          <button type="button" onClick={() => answer(false)}>
            취소
          </button>
          <button type="button" onClick={() => answer(true)}>
            {request.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
