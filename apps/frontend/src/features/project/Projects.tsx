import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Project, Workspace, api } from '../../lib/api';
import WorkspacePicker from '../workspace/WorkspacePicker';

export default function Projects() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [assignTarget, setAssignTarget] = useState<number | null>(null);
  const [assignWorkspaceId, setAssignWorkspaceId] = useState('');
  const [assignAsDefault, setAssignAsDefault] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);

  const load = () => {
    api.listProjects().then(setProjects).catch((e) => setError(String(e)));
    api.listWorkspaces().then(setWorkspaces).catch(() => {});
  };

  useEffect(() => {
    load();
  }, []);

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createProject({ name });
      setName('');
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  const onAssign = async (projectId: number) => {
    if (!assignWorkspaceId) return;
    setError(null);
    try {
      await api.assignProjectWorkspace(projectId, Number(assignWorkspaceId), assignAsDefault);
      setAssignTarget(null);
      setAssignWorkspaceId('');
      setAssignAsDefault(false);
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  const onRemove = async (projectId: number, workspaceId: number) => {
    setError(null);
    try {
      await api.removeProjectWorkspace(projectId, workspaceId);
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  const onSelectFolder = async (selectedPath: string) => {
    setPickerOpen(false);
    try {
      const list = await api.listWorkspaces();
      const found = list.find((w) => w.path === selectedPath);
      if (found) {
        setWorkspaces(list);
        setAssignWorkspaceId(String(found.id));
      } else {
        setError('등록되지 않은 폴더입니다. Workspaces 화면에서 먼저 등록하세요.');
      }
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>Projects</h1>
      <p>
        프로젝트에는 워크스페이스(폴더)를 여러 개 할당할 수 있고, 그중 하나가 기본 작업 디렉터리가 됩니다. 에이전트도
        프로젝트 안에서 만듭니다.
      </p>

      <form onSubmit={onCreate} className="formRow">
        <input placeholder="프로젝트 이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <button type="submit">등록</button>
      </form>
      {error && <p className="errorText">{error}</p>}

      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Workspaces</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {projects.map((p) => (
            <tr key={p.id}>
              <td>
                <Link to={`/projects/${p.id}`}>{p.name}</Link>
                {p.masterAgent != null && <div style={{ color: 'var(--color-muted)', fontSize: 12 }}>마스터 {p.masterAgent.name}</div>}
              </td>
              <td>
                {p.workspaces.length === 0 && <span>할당된 워크스페이스 없음</span>}
                {p.workspaces.map((w) => (
                  <div key={w.workspaceId}>
                    {w.isDefault ? '★ ' : ''}
                    {w.name} — {w.path}
                    <button onClick={() => onRemove(p.id, w.workspaceId)}>해제</button>
                  </div>
                ))}
              </td>
              <td>
                {assignTarget === p.id ? (
                  <div className="formRow">
                    <select value={assignWorkspaceId} onChange={(e) => setAssignWorkspaceId(e.target.value)}>
                      <option value="">워크스페이스 선택</option>
                      {workspaces.map((w) => (
                        <option key={w.id} value={w.id}>
                          {w.name} — {w.path}
                        </option>
                      ))}
                    </select>
                    <button type="button" onClick={() => setPickerOpen(true)}>
                      폴더 찾기
                    </button>
                    <label>
                      <input
                        type="checkbox"
                        checked={assignAsDefault}
                        onChange={(e) => setAssignAsDefault(e.target.checked)}
                      />
                      기본으로
                    </label>
                    <button onClick={() => onAssign(p.id)} disabled={!assignWorkspaceId}>
                      할당
                    </button>
                    <button type="button" onClick={() => setAssignTarget(null)}>
                      취소
                    </button>
                  </div>
                ) : (
                  <button onClick={() => setAssignTarget(p.id)}>워크스페이스 할당</button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {pickerOpen && (
        <WorkspacePicker onSelect={onSelectFolder} onClose={() => setPickerOpen(false)} />
      )}
    </div>
  );
}
