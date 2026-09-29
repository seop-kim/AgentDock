import { useState } from 'react';
import { Agent, AiProvider, api } from '../lib/api';
import styles from './AgentProviderAssign.module.css';

interface AgentProviderAssignProps {
  agent: Agent;
  providers: AiProvider[];
  onAssigned: () => void;
  onError: (message: string | null) => void;
}

export default function AgentProviderAssign({ agent, providers, onAssigned, onError }: AgentProviderAssignProps) {
  const [providerId, setProviderId] = useState('');
  const candidates = providers.filter((p) => p.id !== agent.providerId);

  const onApply = async () => {
    onError(null);
    try {
      await api.assignAgentProvider(agent.id, Number(providerId));
      setProviderId('');
      onAssigned();
    } catch (e) {
      onError(String(e));
    }
  };

  return (
    <span className={styles.row}>
      <select value={providerId} onChange={(e) => setProviderId(e.target.value)}>
        <option value="">Provider 변경</option>
        {candidates.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name} ({p.key})
          </option>
        ))}
      </select>
      <button type="button" onClick={onApply} disabled={providerId === ''}>
        적용
      </button>
    </span>
  );
}
