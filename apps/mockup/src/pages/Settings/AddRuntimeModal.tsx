import { FormEvent, useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import { DEFAULT_PROVIDER_NAMES } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import styles from './AddRuntimeModal.module.css';

export default function AddRuntimeModal({ onClose }: { onClose: () => void }) {
  const { availableProviderKeys, addProvider } = useMockStore();
  const [key, setKey] = useState(availableProviderKeys[0] ?? '');
  const [name, setName] = useState(DEFAULT_PROVIDER_NAMES[availableProviderKeys[0]] ?? '');

  const onKeyChange = (next: string) => {
    setKey(next);
    setName(DEFAULT_PROVIDER_NAMES[next] ?? '');
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    addProvider(key, name.trim());
    onClose();
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <form className={styles.modal} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>런타임 추가</h2>
        {availableProviderKeys.length === 0 ? (
          <p className={shared.hint}>모든 런타임이 이미 등록되어 있습니다. 삭제한 런타임은 다시 등록할 수 있습니다.</p>
        ) : (
          <>
            <p className={shared.hint}>등록한 런타임은 꺼진 상태로 추가됩니다. 카드에서 켜 주세요.</p>
            <select value={key} onChange={(e) => onKeyChange(e.target.value)} required>
              {availableProviderKeys.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
            <input placeholder="표시 이름" value={name} onChange={(e) => setName(e.target.value)} required />
          </>
        )}
        <div className={styles.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={availableProviderKeys.length === 0}>
            추가
          </button>
        </div>
      </form>
    </div>
  );
}
