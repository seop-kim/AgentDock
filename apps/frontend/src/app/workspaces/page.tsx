'use client';

import { FormEvent, useEffect, useState } from 'react';
import { Workspace, api } from '@/lib/api';

export default function WorkspacesPage() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [name, setName] = useState('');
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);

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
      <p>서버의 <code>WORKSPACE_ALLOWED_ROOTS</code>에 등록된 경로 하위만 등록 가능합니다.</p>
      <form onSubmit={onSubmit} style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <input placeholder="이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <input
          placeholder="절대 경로 (예: /home/user/workspace/limo-server)"
          value={path}
          onChange={(e) => setPath(e.target.value)}
          style={{ width: 360 }}
          required
        />
        <button type="submit">등록</button>
      </form>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      <ul>
        {workspaces.map((w) => (
          <li key={w.id}>
            <strong>{w.name}</strong> — {w.path}
          </li>
        ))}
      </ul>
    </div>
  );
}
