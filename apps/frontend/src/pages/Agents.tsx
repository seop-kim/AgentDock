import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Agent, AgentRole, AiProvider, PermissionProfile, Project, api } from '../lib/api';
import AgentProviderAssign from './AgentProviderAssign';
import RunAgentModal from './RunAgentModal';
import styles from './Agents.module.css';

const UNAVAILABLE_LABELS: Record<string, string> = {
  PROVIDER_DELETED: '런타임 삭제됨',
  RUNTIME_DISABLED: '런타임 꺼짐',
  CONNECTION_NOT_CONNECTED: '폴더에서 확인 안 됨',
};

const EMPTY_FORM = {
  name: '',
  projectId: '',
  roleId: '',
  permissionProfileId: '',
  providerId: '',
  persona: '',
  model: '',
  mode: '',
};

export default function Agents() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [roles, setRoles] = useState<AgentRole[]>([]);
  const [permissionProfiles, setPermissionProfiles] = useState<PermissionProfile[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [runTarget, setRunTarget] = useState<Agent | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);

  const loadAll = () => {
    api.listAgents().then(setAgents).catch((e) => setError(String(e)));
    api.listRoles().then(setRoles).catch(() => {});
    api.listPermissionProfiles().then(setPermissionProfiles).catch(() => {});
    api.listProviders().then(setProviders).catch(() => {});
    api.listProjects().then(setProjects).catch(() => {});
  };

  useEffect(() => {
    loadAll();
  }, []);

  const enabledProviders = providers.filter((p) => p.enabled);
  const selectedProvider = enabledProviders.find((p) => String(p.id) === form.providerId) ?? null;
  const modelOptions = selectedProvider?.capabilities?.models ?? [];
  const modeOptions = selectedProvider?.capabilities?.modes ?? [];

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
    if (!form.projectId) {
      setError('에이전트는 프로젝트 안에서 만듭니다. 프로젝트를 선택하세요.');
      return;
    }
    try {
      await api.createAgent({
        ...form,
        projectId: Number(form.projectId),
        roleId: Number(form.roleId),
        permissionProfileId: Number(form.permissionProfileId),
        providerId: Number(form.providerId),
        persona: form.persona || undefined,
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
      <p>
        에이전트는 프로젝트 안에서 만들고, 켜둔 런타임과 이 에이전트만의 페르소나(성격/일하는 방식)를 지정합니다. 실행
        가능 여부는 프로젝트의 기본 워크스페이스에서 그 런타임이 확인됐는지로 결정됩니다.
      </p>

      <form onSubmit={onSubmit} className={styles.form}>
        <input
          placeholder="Agent 이름 (예: Backend Developer A)"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />

        <select value={form.projectId} onChange={(e) => setForm({ ...form, projectId: e.target.value })} required>
          <option value="">프로젝트 선택 (에이전트는 프로젝트 소속)</option>
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
              {p.workspaces.length > 0 ? ` — ${p.workspaces.length}개 워크스페이스` : ' (워크스페이스 없음)'}
            </option>
          ))}
        </select>

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
          <option value="">런타임 선택 (켜둔 것만)</option>
          {enabledProviders.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.key})
            </option>
          ))}
        </select>
        {enabledProviders.length === 0 && (
          <p className={styles.hint}>켜져 있는 런타임이 없습니다. 에이전트 설정에서 런타임을 켜세요.</p>
        )}

        <textarea
          rows={3}
          placeholder="페르소나 (성격/일하는 방식 — 시스템 프롬프트로 덧붙습니다)"
          value={form.persona}
          onChange={(e) => setForm({ ...form, persona: e.target.value })}
        />

        {selectedProvider && (
          <p className={styles.hint}>
            {selectedProvider.name} 지원 목록 — 모델: {modelOptions.length > 0 ? modelOptions.join(', ') : '미등록'} / 모드:{' '}
            {modeOptions.length > 0 ? modeOptions.join(', ') : '미등록'} (에이전트 설정에서 편집)
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

        <button type="submit">Agent 생성</button>
      </form>

      {error && <p className="errorText">{error}</p>}

      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Project</th>
            <th>Role</th>
            <th>Runtime</th>
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
              <td>{a.project?.name ?? '-'}</td>
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
                  title={a.available ? undefined : '사용 불가: 워크스페이스에서 폴더 확인 또는 런타임 on/off 를 확인하세요.'}
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
