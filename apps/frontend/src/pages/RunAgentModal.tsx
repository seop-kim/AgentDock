import { FormEvent, useEffect, useState } from 'react';
import { Project, api } from '../lib/api';
import styles from './RunAgentModal.module.css';

interface RunAgentModalProps {
  agentId: number;
  agentName: string;
  model?: string | null;
  mode?: string | null;
  onClose: () => void;
  onStarted: (executionId: number) => void;
}

export default function RunAgentModal({ agentId, agentName, model, mode, onClose, onStarted }: RunAgentModalProps) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectId, setProjectId] = useState('');
  const [prompt, setPrompt] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .listProjects()
      .then(setProjects)
      .catch((e) => setError(String(e)));
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const execution = await api.createExecution({ agentId, projectId: Number(projectId), prompt });
      onStarted(execution.id);
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <form className={styles.modal} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>Run {agentName}</h2>
        <p className={styles.hint}>작업 디렉터리는 선택한 프로젝트의 기본 워크스페이스로 결정됩니다.</p>
        <p className={styles.hint}>
          모델: {model ?? 'CLI 기본값'} / 모드: {mode ?? 'CLI 기본값'} (Agent 설정을 사용합니다)
        </p>
        <select value={projectId} onChange={(e) => setProjectId(e.target.value)} required>
          <option value="">프로젝트 선택</option>
          {projects.map((p) => {
            const workspace = p.workspaces.find((w) => w.isDefault) ?? p.workspaces[0];
            return (
              <option key={p.id} value={p.id}>
                {p.name}
                {workspace ? ` — ${workspace.path}` : ' (워크스페이스 없음)'}
              </option>
            );
          })}
        </select>
        <textarea
          rows={4}
          placeholder="Agent에게 전달할 Prompt"
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          required
        />
        {error && <p className="errorText">{error}</p>}
        <div className={styles.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={projects.length === 0}>
            실행
          </button>
        </div>
      </form>
    </div>
  );
}
