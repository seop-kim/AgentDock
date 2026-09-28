'use client';

import { FormEvent, useEffect, useState } from 'react';
import { AiProvider, api } from '@/lib/api';

const PROVIDER_KEYS = ['CLAUDE_CODE', 'CODEX', 'COMMAND_CODE', 'GEMINI'];

export default function ProvidersPage() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [key, setKey] = useState(PROVIDER_KEYS[0]);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = () => api.listProviders().then(setProviders).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  const onSubmit = async (e: FormEvent) => {
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

  return (
    <div>
      <h1>AI Providers / Connections</h1>
      <form onSubmit={onSubmit} style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <select value={key} onChange={(e) => setKey(e.target.value)}>
          {PROVIDER_KEYS.map((k) => (
            <option key={k} value={k}>
              {k}
            </option>
          ))}
        </select>
        <input placeholder="표시 이름 (예: Claude Code)" value={name} onChange={(e) => setName(e.target.value)} required />
        <button type="submit">등록</button>
      </form>
      {error && <p style={{ color: 'red' }}>{error}</p>}
      <table cellPadding={8}>
        <thead>
          <tr>
            <th>Key</th>
            <th>Name</th>
            <th>Connections</th>
          </tr>
        </thead>
        <tbody>
          {providers.map((p) => (
            <tr key={p.id}>
              <td>{p.key}</td>
              <td>{p.name}</td>
              <td>{(p as any).connections?.length ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
