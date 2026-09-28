'use client';

import { FormEvent, useEffect, useState } from 'react';
import { Workspace, api } from '@/lib/api';
import WorkspacePicker from './WorkspacePicker';
import styles from './page.module.css';

export default function WorkspacesPage() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [name, setName] = useState('');
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const load = () => api.listWorkspaces().then(setWorkspaces).catch((e) => setError(String(e)));

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

  return (
    <div>
      <h1>Workspaces</h1>
      <p>Agent가 작업할 로컬 폴더를 선택하세요.</p>
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
