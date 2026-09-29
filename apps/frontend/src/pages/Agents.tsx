import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Agent, AgentRole, AiProvider, PermissionProfile, api } from '../lib/api';
import AgentProviderAssign from './AgentProviderAssign';
import RunAgentModal from './RunAgentModal';
import styles from './Agents.module.css';

const UNAVAILABLE_LABELS: Record<string, string> = {
  PROVIDER_DELETED: 'Provider 삭제됨',
  CONNECTION_NOT_CONNECTED: '연결 안 됨',
};

const EMPTY_FORM = { name: '', roleId: '', permissionProfileId: '', providerId: '', model: '', mode: '' };

export default function Agents() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [roles, setRoles] = useState<AgentRole[]>([]);
  const [permissionProfiles, setPermissionProfiles] = useState<PermissionProfile[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [runTarget, setRunTarget] = useState<Agent | null>(null);

  const [form, setForm] = useState(EMPTY_FORM);

  const loadAll = () => {
    api.listAgents().then(setAgents).catch((e) => setError(String(e)));
    api.listRoles().then(setRoles).catch(() => {});
    api.listPermissionProfiles().then(setPermissionProfiles).catch(() => {});
    api.listProviders().then(setProviders).catch(() => {});
  };

  useEffect(() => {
    loadAll();
  }, []);

  const selectedProvider = providers.find((p) => String(p.id) === form.providerId) ?? null;
  const modelOptions = selectedProvider?.capabilities?.models ?? [];
  const modeOptions = selectedProvider?.capabilities?.modes ?? [];
  const modelNotListed = form.model !== '' && modelOptions.length > 0 && !modelOptions.includes(form.model);
  const modeNotListed = form.mode !== '' && modeOptions.length > 0 && !modeOptions.includes(form.mode);

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
        mode: form.mode || undefined,
      });
      setForm(EMPTY_FORM);
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

        {selectedProvider && (
          <p className={styles.hint}>
            {selectedProvider.name} 지원 목록 — 모델: {modelOptions.length > 0 ? modelOptions.join(', ') : '미등록'} / 모드:{' '}
            {modeOptions.length > 0 ? modeOptions.join(', ') : '미등록'} (Agent 연결 설정에서 편집)
          </p>
        )}

        <input
          list="agent-model-options"
          placeholder="Model (선택 — 목록에서 고르거나 직접 입력)"
          value={form.model}
          onChange={(e) => setForm({ ...form, model: e.target.value })}
        />
        <datalist id="agent-model-options">
          {modelOptions.map((m) => (
            <option key={m} value={m} />
          ))}
        </datalist>

        <input
          list="agent-mode-options"
          placeholder="Mode (선택 — 예: plan, acceptEdits)"
          value={form.mode}
          onChange={(e) => setForm({ ...form, mode: e.target.value })}
        />
        <datalist id="agent-mode-options">
          {modeOptions.map((m) => (
            <option key={m} value={m} />
          ))}
        </datalist>

        {modelNotListed && (
          <p className={styles.hint}>
            모델 "{form.model}" 은 {selectedProvider?.name} 지원 목록에 없습니다. CLI가 지원하면 그대로 동작합니다.
          </p>
        )}
        {modeNotListed && (
          <p className={styles.hint}>
            모드 "{form.mode}" 은 {selectedProvider?.name} 지원 목록에 없습니다. CLI가 지원하면 그대로 동작합니다.
          </p>
        )}

        <button type="submit">Agent 생성</button>
      </form>

      {error && <p className="errorText">{error}</p>}

      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Role</th>
            <th>Provider</th>
            <th>Model / Mode</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.id}>
              <td>
                {a.name}
                {!a.available && a.unavailableReason && (
                  <span className={styles.unavailable}>{UNAVAILABLE_LABELS[a.unavailableReason] ?? '사용 불가'}</span>
                )}
              </td>
              <td>{a.role.name}</td>
              <td>
                {a.provider.name}{' '}
                <AgentProviderAssign agent={a} providers={providers} onAssigned={loadAll} onError={setError} />
              </td>
              <td className={styles.hint}>
                {a.model ?? 'CLI 기본값'} / {a.mode ?? 'CLI 기본값'}
              </td>
              <td>
                <button
                  onClick={() => setRunTarget(a)}
                  disabled={!a.available}
                  title={a.available ? undefined : '사용 불가 상태입니다. Agent 연결 설정에서 연결을 확인하세요.'}
                >
                  Run
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {runTarget && (
        <RunAgentModal
          agentId={runTarget.id}
          agentName={runTarget.name}
          model={runTarget.model}
          mode={runTarget.mode}
          onClose={() => setRunTarget(null)}
          onStarted={(executionId) => navigate(`/executions/${executionId}`)}
        />
      )}
    </div>
  );
}
