import { FormEvent, useEffect, useState } from 'react';
import { Workspace, WorkspaceRuntime, api } from '../../lib/api';
import WorkspacePicker from './WorkspacePicker';
import styles from './page.module.css';

export default function Workspaces() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [runtimes, setRuntimes] = useState<Record<number, WorkspaceRuntime[]>>({});
  const [checking, setChecking] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const loadRuntimes = async (workspaceId: number) => {
    try {
      const list = await api.listWorkspaceRuntimes(workspaceId);
      setRuntimes((prev) => ({ ...prev, [workspaceId]: list }));
    } catch (e) {
      setError(String(e));
    }
  };

  const load = () =>
    api
      .listWorkspaces()
      .then((list) => {
        setWorkspaces(list);
        list.forEach((w) => loadRuntimes(w.id));
      })
      .catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createWorkspace({ name, path });
      setName('');
      setPath('');
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  const onCheck = async (workspaceId: number, providerId: number) => {
    setError(null);
    setChecking(`${workspaceId}:${providerId}`);
    try {
      await api.checkWorkspaceRuntime(workspaceId, providerId);
      await loadRuntimes(workspaceId);
    } catch (e) {
      setError(String(e));
    } finally {
      setChecking(null);
    }
  };

  return (
    <div>
      <h1>Workspaces</h1>
      <p>
        폴더를 등록하고, 그 폴더에서 각 런타임이 실제로 실행되는지 확인합니다. 상태는 폴더별로 따로 기록됩니다(로그인은
        런타임 전역이며 에이전트 설정에서 합니다).
      </p>
      <form onSubmit={onSubmit} className="formRow">
        <input placeholder="이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <input
          placeholder="폴더 선택 버튼으로 지정하세요"
          value={path}
          readOnly
          className={styles.pathInput}
          required
        />
        <button type="button" onClick={() => setPickerOpen(true)}>
          폴더 선택
        </button>
        <button type="submit">등록</button>
      </form>
      {error && <p className="errorText">{error}</p>}
      <ul className={styles.list}>
        {workspaces.map((w) => (
          <li key={w.id}>
            <strong>{w.name}</strong> — {w.path}
            {(runtimes[w.id] ?? []).length === 0 ? (
              <p>켜져 있는 런타임이 없습니다. 에이전트 설정에서 런타임을 켜세요.</p>
            ) : (
              <table className="table" cellPadding={6}>
                <thead>
                  <tr>
                    <th>런타임</th>
                    <th>상태</th>
                    <th>마지막 확인</th>
                    <th>오류</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {(runtimes[w.id] ?? []).map((r) => (
                    <tr key={r.providerId}>
                      <td>
                        {r.name} ({r.providerKey})
                      </td>
                      <td>{r.status}</td>
                      <td>{r.lastCheckedAt ? new Date(r.lastCheckedAt).toLocaleString() : '-'}</td>
                      <td>{r.lastError ?? '-'}</td>
                      <td>
                        <button
                          onClick={() => onCheck(w.id, r.providerId)}
                          disabled={checking === `${w.id}:${r.providerId}`}
                        >
                          {checking === `${w.id}:${r.providerId}` ? '확인 중...' : '이 폴더에서 확인'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </li>
        ))}
      </ul>
      {pickerOpen && (
        <WorkspacePicker
          onSelect={(selected) => {
            setPath(selected);
            setPickerOpen(false);
          }}
          onClose={() => setPickerOpen(false)}
        />
      )}
    </div>
  );
}
