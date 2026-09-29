import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Agent, AgentRole, AiProvider, PermissionProfile, api } from '../lib/api';
import RunAgentModal from './RunAgentModal';
import styles from './Agents.module.css';

export default function Agents() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [roles, setRoles] = useState<AgentRole[]>([]);
  const [permissionProfiles, setPermissionProfiles] = useState<PermissionProfile[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [runTarget, setRunTarget] = useState<Agent | null>(null);

  const [form, setForm] = useState({
    name: '',
    roleId: '',
    permissionProfileId: '',
    providerId: '',
    model: '',
  });

  const loadAll = () => {
    api.listAgents().then(setAgents).catch((e) => setError(String(e)));
    api.listRoles().then(setRoles).catch(() => {});
    api.listPermissionProfiles().then(setPermissionProfiles).catch(() => {});
    api.listProviders().then(setProviders).catch(() => {});
  };

  useEffect(() => {
    loadAll();
  }, []);

  const quickCreateRole = async () => {
    const name = window.prompt('Role 이름 (예: Backend Developer)');
    if (!name) return;
    await api.createRole({ name });
    loadAll();
  };

  const quickCreatePermissionProfile = async () => {
    const name = window.prompt('Permission Profile 이름 (예: Backend Developer Default)');
    if (!name) return;
    await api.createPermissionProfile({
      name,
      fileRead: true,
      fileWrite: true,
      terminalExecute: true,
      gitDiff: true,
    });
    loadAll();
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createAgent({
        ...form,
        roleId: Number(form.roleId),
        permissionProfileId: Number(form.permissionProfileId),
        providerId: Number(form.providerId),
        model: form.model || undefined,
      });
      setForm({ name: '', roleId: '', permissionProfileId: '', providerId: '', model: '' });
      loadAll();
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>Agents</h1>

      <form onSubmit={onSubmit} className={styles.form}>
        <input
          placeholder="Agent 이름 (예: Backend Developer A)"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />

        <div className="formRow">
          <select value={form.roleId} onChange={(e) => setForm({ ...form, roleId: e.target.value })} required>
            <option value="">Role 선택</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
          <button type="button" onClick={quickCreateRole}>
            + Role
          </button>
        </div>

        <div className="formRow">
          <select
            value={form.permissionProfileId}
            onChange={(e) => setForm({ ...form, permissionProfileId: e.target.value })}
            required
          >
            <option value="">Permission Profile 선택</option>
            {permissionProfiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <button type="button" onClick={quickCreatePermissionProfile}>
            + Profile
          </button>
        </div>

        <select value={form.providerId} onChange={(e) => setForm({ ...form, providerId: e.target.value })} required>
          <option value="">AI Provider 선택</option>
          {providers.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.key})
            </option>
          ))}
        </select>

        <input
          placeholder="Model (예: claude-sonnet-5, 선택)"
          value={form.model}
          onChange={(e) => setForm({ ...form, model: e.target.value })}
        />

        <button type="submit">Agent 생성</button>
      </form>

      {error && <p className="errorText">{error}</p>}

      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Role</th>
            <th>Provider</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.id}>
              <td>{a.name}</td>
              <td>{a.role.name}</td>
              <td>{a.provider.name}</td>
              <td>
                <button onClick={() => setRunTarget(a)}>Run</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {runTarget && (
        <RunAgentModal
          agentId={runTarget.id}
          agentName={runTarget.name}
          onClose={() => setRunTarget(null)}
          onStarted={(executionId) => navigate(`/executions/${executionId}`)}
        />
      )}
    </div>
  );
}
