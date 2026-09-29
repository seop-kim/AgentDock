import { useState } from 'react';
import { AiProvider, api } from '../lib/api';
import LoginPanel from './LoginPanel';
import styles from './Providers.module.css';

interface ProviderCardProps {
  provider: AiProvider;
  onChanged: () => void;
  onError: (message: string | null) => void;
}

// 목록이 자주 바뀌므로 화면에서 직접 갱신한다(코드 수정 불필요). 줄바꿈 또는 콤마로 구분한다.
const toLines = (values?: string[] | null) => (values ?? []).join('\n');

const parseLines = (text: string) =>
  Array.from(new Set(text.split(/[\n,]/).map((value) => value.trim()).filter((value) => value !== '')));

export default function ProviderCard({ provider, onChanged, onError }: ProviderCardProps) {
  const connection = provider.connections?.[0] ?? null;
  const [checking, setChecking] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [capabilitiesOpen, setCapabilitiesOpen] = useState(false);
  const [modelsText, setModelsText] = useState('');
  const [modesText, setModesText] = useState('');
  const [notesText, setNotesText] = useState('');

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

  const openCapabilities = () => {
    setModelsText(toLines(provider.capabilities?.models));
    setModesText(toLines(provider.capabilities?.modes));
    setNotesText(provider.capabilities?.notes ?? '');
    setCapabilitiesOpen(true);
  };

  const onSaveCapabilities = () =>
    run(() =>
      api.updateProviderCapabilities(provider.id, {
        models: parseLines(modelsText),
        modes: parseLines(modesText),
        notes: notesText.trim() === '' ? null : notesText.trim(),
      }),
    ).then(() => setCapabilitiesOpen(false));

  const models = provider.capabilities?.models ?? [];
  const modes = provider.capabilities?.modes ?? [];

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

      <p className={styles.checkedAt}>
        모델: {models.length > 0 ? models.join(', ') : '미등록'} / 모드: {modes.length > 0 ? modes.join(', ') : '미등록'}
      </p>
      <div className={styles.actions}>
        <button onClick={() => (capabilitiesOpen ? setCapabilitiesOpen(false) : openCapabilities())}>
          {capabilitiesOpen ? '모델/모드 닫기' : '모델/모드 편집'}
        </button>
      </div>
      {capabilitiesOpen && (
        <div className={styles.loginPanel}>
          <p className={styles.hint}>
            줄바꿈 또는 콤마로 구분합니다. 목록은 코드가 아니라 데이터라서 여기서 갱신하면 Agent 화면에 바로 반영됩니다.
          </p>
          <label className={styles.checkedAt}>모델</label>
          <textarea rows={3} value={modelsText} onChange={(e) => setModelsText(e.target.value)} placeholder={'opus\nsonnet'} />
          <label className={styles.checkedAt}>모드 (CLI 의 실행/권한 모드)</label>
          <textarea rows={3} value={modesText} onChange={(e) => setModesText(e.target.value)} placeholder={'plan\nacceptEdits'} />
          <label className={styles.checkedAt}>메모 (선택)</label>
          <input value={notesText} onChange={(e) => setNotesText(e.target.value)} placeholder="예: CLI 버전에 따라 바뀜" />
          <div className={styles.actions}>
            <button onClick={onSaveCapabilities}>저장</button>
          </div>
        </div>
      )}
    </div>
  );
}
