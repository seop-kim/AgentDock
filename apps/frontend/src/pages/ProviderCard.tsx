import { useState } from 'react';
import { AiProvider, api } from '../lib/api';
import LoginPanel from './LoginPanel';
import styles from './Providers.module.css';

interface ProviderCardProps {
  provider: AiProvider;
  onChanged: () => void;
  onError: (message: string | null) => void;
}

export default function ProviderCard({ provider, onChanged, onError }: ProviderCardProps) {
  const connection = provider.connections?.[0] ?? null;
  const [checking, setChecking] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);

  const run = async (action: () => Promise<unknown>) => {
    onError(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      onError(String(e));
    }
  };

  const onCheck = async () => {
    if (!connection) return;
    setChecking(true);
    await run(() => api.checkConnection(connection.id));
    setChecking(false);
  };

  const onLoginExit = async (exitCode: number) => {
    if (exitCode !== 0 || !connection) return;
    await run(() => api.checkConnection(connection.id));
  };

  const onDeleteConnection = () => {
    if (!connection) return;
    if (!window.confirm('연결을 삭제하면 이 Provider의 Agent는 연결이 복구되기 전까지 실행되지 않습니다. 삭제할까요?')) return;
    setLoginOpen(false);
    run(() => api.deleteConnection(connection.id));
  };

  const onDeleteProvider = () => {
    if (!window.confirm('이 Provider를 쓰는 Agent는 사용 불가가 되며 다른 Provider를 다시 할당해야 합니다. 삭제할까요?')) return;
    run(() => api.deleteProvider(provider.id));
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <h2 className={styles.cardTitle}>
          {provider.name} <span className={styles.checkedAt}>({provider.key})</span>
        </h2>
        <button className={styles.dangerButton} onClick={onDeleteProvider}>
          Provider 삭제
        </button>
      </div>

      {connection ? (
        <>
          <div className={styles.statusRow}>
            <span
              className={
                connection.status === 'CONNECTED' ? `${styles.badge} ${styles.badgeOk}` : `${styles.badge} ${styles.badgeError}`
              }
            >
              {connection.status}
            </span>
            <span className={styles.checkedAt}>
              Last checked: {connection.lastCheckedAt ? new Date(connection.lastCheckedAt).toLocaleString() : '-'}
            </span>
          </div>
          {connection.lastError && <p className={styles.connError}>{connection.lastError}</p>}
          <div className={styles.actions}>
            <button onClick={onCheck} disabled={checking}>
              {checking ? '확인 중...' : '연결'}
            </button>
            {connection.status === 'ERROR' && (
              <button onClick={() => setLoginOpen(true)} disabled={loginOpen}>
                로그인
              </button>
            )}
            <button className={styles.dangerButton} onClick={onDeleteConnection}>
              연결 삭제
            </button>
          </div>
          {loginOpen && (
            <LoginPanel connectionId={connection.id} onClose={() => setLoginOpen(false)} onExit={onLoginExit} />
          )}
        </>
      ) : (
        <>
          <p className={styles.checkedAt}>Connection이 없습니다. 이 Provider의 Agent는 실행되지 않습니다.</p>
          <div className={styles.actions}>
            <button onClick={() => run(() => api.createProviderConnection(provider.id))}>연결 추가</button>
          </div>
        </>
      )}
    </div>
  );
}
