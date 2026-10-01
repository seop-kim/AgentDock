import { FormEvent, useState } from 'react';
import { api } from '../../lib/api';
import styles from './AddProviderModal.module.css';

const DEFAULT_NAMES: Record<string, string> = {
  CLAUDE_CODE: 'Claude Code',
  CODEX: 'Codex',
  COMMAND_CODE: 'Command Code',
  GEMINI: 'Gemini',
};

interface AddProviderModalProps {
  registeredKeys: string[];
  onClose: () => void;
  onCreated: () => void;
}

export default function AddProviderModal({ registeredKeys, onClose, onCreated }: AddProviderModalProps) {
  const availableKeys = Object.keys(DEFAULT_NAMES).filter((k) => !registeredKeys.includes(k));
  const [key, setKey] = useState(availableKeys[0] ?? '');
  const [name, setName] = useState(DEFAULT_NAMES[availableKeys[0]] ?? '');
  const [error, setError] = useState<string | null>(null);

  const onKeyChange = (next: string) => {
    setKey(next);
    setName(DEFAULT_NAMES[next] ?? '');
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createProvider({ key, name });
      onCreated();
    } catch (err) {
      setError(String(err));
    }
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <form className={styles.modal} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>Provider 추가</h2>
        {availableKeys.length === 0 ? (
          <p className={styles.hint}>모든 Provider가 이미 등록되어 있습니다. 삭제한 Provider는 다시 등록할 수 있습니다.</p>
        ) : (
          <>
            <p className={styles.hint}>Provider를 등록하면 연결(Connection)이 함께 만들어집니다.</p>
            <select value={key} onChange={(e) => onKeyChange(e.target.value)} required>
              {availableKeys.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
            <input placeholder="표시 이름" value={name} onChange={(e) => setName(e.target.value)} required />
          </>
        )}
        {error && <p className="errorText">{error}</p>}
        <div className={styles.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={availableKeys.length === 0}>
            추가
          </button>
        </div>
      </form>
    </div>
  );
}
