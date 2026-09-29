import { FormEvent, useEffect, useState } from 'react';
import { Agent, AgentGroup, Project, api } from '../lib/api';
import styles from './Groups.module.css';

export default function Groups() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [groups, setGroups] = useState<AgentGroup[]>([]);
  const [projectId, setProjectId] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [leaderAgentId, setLeaderAgentId] = useState('');
  const [memberSelection, setMemberSelection] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listProjects().then(setProjects).catch((e) => setError(String(e)));
    api.listAgents().then(setAgents).catch((e) => setError(String(e)));
  }, []);

  const loadGroups = (id: string) => {
    if (!id) {
      setGroups([]);
      return;
    }
    api.listGroups(Number(id)).then(setGroups).catch((e) => setError(String(e)));
  };

  useEffect(() => {
    loadGroups(projectId);
  }, [projectId]);

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createGroup({
        projectId: Number(projectId),
        name,
        description: description || undefined,
        leaderAgentId: leaderAgentId ? Number(leaderAgentId) : undefined,
      });
      setName('');
      setDescription('');
      setLeaderAgentId('');
      loadGroups(projectId);
    } catch (e) {
      setError(String(e));
    }
  };

  const onAddMember = async (group: AgentGroup) => {
    const selected = memberSelection[group.id];
    if (!selected) return;
    setError(null);
    try {
      const updated = await api.addGroupMember(group.id, Number(selected));
      setGroups((prev) => prev.map((g) => (g.id === updated.id ? updated : g)));
      setMemberSelection((prev) => ({ ...prev, [group.id]: '' }));
    } catch (e) {
      setError(String(e));
    }
  };

  const onRemoveMember = async (group: AgentGroup, agentId: number) => {
    setError(null);
    try {
      const updated = await api.removeGroupMember(group.id, agentId);
      setGroups((prev) => prev.map((g) => (g.id === updated.id ? updated : g)));
    } catch (e) {
      setError(String(e));
    }
  };

  const onSetLeader = async (group: AgentGroup, agentId: string) => {
    setError(null);
    try {
      const updated = await api.updateGroup(group.id, {
        name: group.name,
        description: group.description ?? undefined,
        leaderAgentId: agentId ? Number(agentId) : null,
      });
      setGroups((prev) => prev.map((g) => (g.id === updated.id ? updated : g)));
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>Groups</h1>
      <p>그룹은 프로젝트를 수행하는 팀입니다. 그룹의 리더가 태스크 실행을 담당합니다.</p>

      <div className="formRow">
        <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
          <option value="">프로젝트 선택</option>
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>

      {projectId && (
        <form onSubmit={onCreate} className="formRow">
          <input
            placeholder="그룹 이름 (예: Backend Team)"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <input placeholder="설명 (선택)" value={description} onChange={(e) => setDescription(e.target.value)} />
          <select value={leaderAgentId} onChange={(e) => setLeaderAgentId(e.target.value)}>
            <option value="">리더 선택 (나중에 지정 가능)</option>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
          <button type="submit">그룹 생성</button>
        </form>
      )}

      {error && <p className="errorText">{error}</p>}

      {groups.map((group) => (
        <div key={group.id} className={styles.card}>
          <h2 className={styles.cardTitle}>{group.name}</h2>
          <p className={styles.muted}>{group.description || '설명 없음'}</p>

          <div className="formRow">
            <span>리더</span>
            <select value={group.leader?.id ?? ''} onChange={(e) => onSetLeader(group, e.target.value)}>
              <option value="">리더 없음</option>
              {agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>

          <p>멤버 ({group.members.length})</p>
          <ul>
            {group.members.map((m) => (
              <li key={m.id}>
                {m.name}{' '}
                <button type="button" onClick={() => onRemoveMember(group, m.id)}>
                  제거
                </button>
              </li>
            ))}
          </ul>

          <div className="formRow">
            <select
              value={memberSelection[group.id] ?? ''}
              onChange={(e) => setMemberSelection((prev) => ({ ...prev, [group.id]: e.target.value }))}
            >
              <option value="">에이전트 선택</option>
              {agents
                .filter((a) => !group.members.some((m) => m.id === a.id))
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
            </select>
            <button type="button" onClick={() => onAddMember(group)}>
              멤버 추가
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
