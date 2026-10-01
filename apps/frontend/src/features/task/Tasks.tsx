import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Agent, AgentGroup, Project, Task, api } from '../../lib/api';
import styles from './Tasks.module.css';

export default function Tasks() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>([]);
  const [groups, setGroups] = useState<AgentGroup[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [projectId, setProjectId] = useState('');
  const [assigneeType, setAssigneeType] = useState<'group' | 'agent'>('group');
  const [groupId, setGroupId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [title, setTitle] = useState('');
  const [prompt, setPrompt] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listProjects().then(setProjects).catch((e) => setError(String(e)));
    api.listAgents().then(setAgents).catch((e) => setError(String(e)));
  }, []);

  const loadTasks = (id: string) => {
    if (!id) {
      setTasks([]);
      return;
    }
    api.listTasks(Number(id)).then(setTasks).catch((e) => setError(String(e)));
    api.listGroups(Number(id)).then(setGroups).catch((e) => setError(String(e)));
  };

  useEffect(() => {
    loadTasks(projectId);
  }, [projectId]);

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createTask({
        projectId: Number(projectId),
        title,
        prompt,
        groupId: assigneeType === 'group' && groupId ? Number(groupId) : undefined,
        agentId: assigneeType === 'agent' && agentId ? Number(agentId) : undefined,
      });
      setTitle('');
      setPrompt('');
      loadTasks(projectId);
    } catch (e) {
      setError(String(e));
    }
  };

  const onRun = async (task: Task) => {
    setError(null);
    try {
      const execution = await api.runTask(task.id);
      navigate(`/executions/${execution.id}`);
    } catch (e) {
      setError(String(e));
    }
  };

  const assigneeLabel = (task: Task) =>
    task.group ? `그룹: ${task.group.name}` : task.agent ? `Agent: ${task.agent.name}` : '-';

  return (
    <div>
      <h1>Tasks</h1>
      <p>
        태스크는 프로젝트에 속하고, 그룹 또는 개별 Agent에 할당됩니다. Run은 그룹 리더(또는 지정 Agent)에게 실행을
        위임합니다.
      </p>

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
        <form onSubmit={onCreate} className={styles.form}>
          <input placeholder="태스크 제목" value={title} onChange={(e) => setTitle(e.target.value)} required />
          <textarea
            className={styles.textarea}
            rows={4}
            placeholder="Agent에게 전달할 Prompt"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            required
          />
          <div className="formRow">
            <select value={assigneeType} onChange={(e) => setAssigneeType(e.target.value as 'group' | 'agent')}>
              <option value="group">그룹에 할당</option>
              <option value="agent">개별 Agent에 할당</option>
            </select>
            {assigneeType === 'group' ? (
              <select value={groupId} onChange={(e) => setGroupId(e.target.value)} required>
                <option value="">그룹 선택</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                    {g.leader ? ` (리더: ${g.leader.name})` : ' (리더 없음)'}
                  </option>
                ))}
              </select>
            ) : (
              <select value={agentId} onChange={(e) => setAgentId(e.target.value)} required>
                <option value="">Agent 선택</option>
                {agents.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
              </select>
            )}
          </div>
          <button type="submit">태스크 생성</button>
        </form>
      )}

      {error && <p className="errorText">{error}</p>}

      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Title</th>
            <th>Assignee</th>
            <th>Status</th>
            <th>Execution</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id}>
              <td>{task.title}</td>
              <td>{assigneeLabel(task)}</td>
              <td>{task.status}</td>
              <td>
                {task.latestExecutionId ? (
                  <Link to={`/executions/${task.latestExecutionId}`}>#{task.latestExecutionId}</Link>
                ) : (
                  '-'
                )}
              </td>
              <td>
                <button onClick={() => onRun(task)}>Run</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
