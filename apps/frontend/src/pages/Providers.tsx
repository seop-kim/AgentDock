import { FormEvent, useEffect, useState } from 'react';
import { AiProvider, api } from '../lib/api';
import styles from './Providers.module.css';

const PROVIDER_KEYS = ['CLAUDE_CODE', 'CODEX', 'COMMAND_CODE', 'GEMINI'];

export default function Providers() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [key, setKey] = useState(PROVIDER_KEYS[0]);
  const [name, setName] = useState('');
  const [connProviderId, setConnProviderId] = useState('');
  const [accountName, setAccountName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [checkingId, setCheckingId] = useState<number | null>(null);

  const load = () => api.listProviders().then(setProviders).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  const onCreateProvider = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createProvider({ key, name });
      setName('');
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  const onCreateConnection = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createConnection({ providerId: Number(connProviderId), accountName: accountName || undefined });
      setAccountName('');
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  const onCheck = async (id: number) => {
    setError(null);
    setCheckingId(id);
    try {
      await api.checkConnection(id);
      await load();
    } catch (e) {
      setError(String(e));
    } finally {
      setCheckingId(null);
    }
  };

  return (
    <div>
      <h1>AI Providers / Connections</h1>
      <p>
        CLI 로그인 세션을 그대로 사용합니다(자격증명은 이 앱에 저장하지 않습니다). 연결 확인은 해당 CLI를 한 번 실행해
        실제로 응답하는지 봅니다.
      </p>

      <form onSubmit={onCreateProvider} className="formRow">
        <select value={key} onChange={(e) => setKey(e.target.value)}>
          {PROVIDER_KEYS.map((k) => (
            <option key={k} value={k}>
              {k}
            </option>
          ))}
        </select>
        <input placeholder="표시 이름 (예: Claude Code)" value={name} onChange={(e) => setName(e.target.value)} required />
        <button type="submit">Provider 등록</button>
      </form>

      {providers.length > 0 && (
        <form onSubmit={onCreateConnection} className="formRow">
          <select value={connProviderId} onChange={(e) => setConnProviderId(e.target.value)} required>
            <option value="">Provider 선택</option>
            {providers.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.key})
              </option>
            ))}
          </select>
          <input
            placeholder="계정 이름 (선택, 예: seop@example.com)"
            value={accountName}
            onChange={(e) => setAccountName(e.target.value)}
          />
          <button type="submit">Connection 추가</button>
        </form>
      )}

      {error && <p className="errorText">{error}</p>}

      {providers.map((provider) => (
        <div key={provider.id} className={styles.card}>
          <h2 className={styles.cardTitle}>
            {provider.name} <span className={styles.checkedAt}>({provider.key})</span>
          </h2>
          {(provider.connections ?? []).length === 0 ? (
            <p className={styles.checkedAt}>Connection이 없습니다. 위에서 추가하세요.</p>
          ) : (
            <table className="table" cellPadding={8}>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Status</th>
                  <th>Last checked</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {(provider.connections ?? []).map((conn) => (
                  <tr key={conn.id}>
                    <td>{conn.accountName ?? '-'}</td>
                    <td>
                      <span
                        className={
                          conn.status === 'CONNECTED' ? `${styles.badge} ${styles.badgeOk}` : `${styles.badge} ${styles.badgeError}`
                        }
                      >
                        {conn.status}
                      </span>
                      {conn.lastError && <p className={styles.connError}>{conn.lastError}</p>}
                    </td>
                    <td className={styles.checkedAt}>
                      {conn.lastCheckedAt ? new Date(conn.lastCheckedAt).toLocaleString() : '-'}
                    </td>
                    <td>
                      <button onClick={() => onCheck(conn.id)} disabled={checkingId === conn.id}>
                        {checkingId === conn.id ? '확인 중...' : '연결 확인'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ))}
    </div>
  );
}
