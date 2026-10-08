import { FormEvent, useEffect, useState } from 'react';
import { api, type ProviderCatalogEntry } from '../../lib/api';
import { useAgentDockStore } from '../../store/AgentDockStore';
import { DEFAULT_PROVIDER_NAMES } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import modal from '../../styles/modal.module.css';

interface Option {
  key: string;
  name: string;
}

export default function AddRuntimeModal({ onClose }: { onClose: () => void }) {
  const { availableProviderKeys, addProvider } = useAgentDockStore();
  const [catalog, setCatalog] = useState<ProviderCatalogEntry[]>([]);

  // 등록 가능한 런타임은 서버 카탈로그에서 온다(구버전 서버면 기본값으로 내려간다).
  useEffect(() => {
    api.getProviderCatalog().then(setCatalog).catch(() => setCatalog([]));
  }, []);

  // 카탈로그가 `registered` 를 주면 그 값을, 아니면(구버전) 스토어의 미등록 키 목록을 쓴다.
  const hasRegisteredFlag = catalog.some((entry) => typeof entry.registered === 'boolean');
  const catalogOptions = catalog.filter((entry) =>
    hasRegisteredFlag ? entry.registered !== true : availableProviderKeys.includes(entry.key),
  );
  const options: Option[] =
    catalog.length > 0
      ? catalogOptions.map((entry) => ({ key: entry.key, name: entry.name }))
      : availableProviderKeys.map((key) => ({ key, name: DEFAULT_PROVIDER_NAMES[key] ?? key }));

  const [key, setKey] = useState(options[0]?.key ?? '');
  const [name, setName] = useState(options[0]?.name ?? '');

  const onKeyChange = (next: string) => {
    setKey(next);
    setName(options.find((option) => option.key === next)?.name ?? DEFAULT_PROVIDER_NAMES[next] ?? '');
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    addProvider(key, name.trim());
    onClose();
  };

  return (    <div className={modal.overlay} onClick={onClose}>
      <form className={modal.modal} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>런타임 추가</h2>
        {options.length === 0 ? (
          <p className={shared.hint}>모든 런타임이 이미 등록되어 있습니다. 삭제한 런타임은 다시 등록할 수 있습니다.</p>
        ) : (
          <>
            <p className={shared.hint}>등록한 런타임은 꺼진 상태로 추가됩니다. 카드에서 켜 주세요.</p>
            <select value={key} onChange={(e) => onKeyChange(e.target.value)} required>
              {options.map((option) => (
                <option key={option.key} value={option.key}>
                  {option.name} ({option.key})
                </option>
              ))}
            </select>
            <input placeholder="표시 이름" value={name} onChange={(e) => setName(e.target.value)} required />
          </>
        )}
        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={options.length === 0}>
            추가
          </button>
        </div>
      </form>
    </div>
  );
}