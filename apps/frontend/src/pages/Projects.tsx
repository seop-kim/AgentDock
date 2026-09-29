import { FormEvent, useEffect, useState } from 'react';
import { Project, api } from '../lib/api';
import WorkspacePicker from './Workspaces/WorkspacePicker';

export default function Projects() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [name, setName] = useState('');
  const [path, setPath] = useState('');
  const [workspaceId, setWorkspaceId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const load = () => api.listProjects().then(setProjects).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  const onSelectWorkspace = async (selected: string) => {
    setPath(selected);
    setPickerOpen(false);
    try {
      const workspaces = await api.listWorkspaces();
      const found = workspaces.find((w) => w.path === selected);
      if (found) {
        setWorkspaceId(found.id);
      } else {
        setError('등록되지 않은 폴더입니다. Workspaces 화면에서 먼저 등록하세요.');
      }
    } catch (e) {
      setError(String(e));
    }
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!workspaceId) {
      setError('워크스페이스를 선택하세요.');
      return;
    }
    try {
      await api.createProject({ name, workspaceId });
      setName('');
      setPath('');
      setWorkspaceId(null);
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>Projects</h1>
      <p>프로젝트가 작업 디렉터리(워크스페이스)를 결정합니다. 그룹과 태스크는 프로젝트 안에 속합니다.</p>
      <form onSubmit={onSubmit} className="formRow">
        <input placeholder="프로젝트 이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <input placeholder="워크스페이스 폴더를 선택하세요" value={path} readOnly required />
        <button type="button" onClick={() => setPickerOpen(true)}>
          폴더 선택
        </button>
        <button type="submit">등록</button>
      </form>
      {error && <p className="errorText">{error}</p>}
      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Workspace</th>
          </tr>
        </thead>
        <tbody>
          {projects.map((p) => (
            <tr key={p.id}>
              <td>{p.name}</td>
              <td>{p.workspace?.path ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {pickerOpen && (
        <WorkspacePicker onSelect={onSelectWorkspace} onClose={() => setPickerOpen(false)} />
      )}
    </div>
  );
}
