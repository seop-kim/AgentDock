import { useEffect, useState } from 'react';
import { AiProvider, api } from '../lib/api';
import AddProviderModal from './AddProviderModal';
import ProviderCard from './ProviderCard';
import styles from './Providers.module.css';

export default function Providers() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = () => api.listProviders().then(setProviders).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <div className={styles.header}>
        <h1>에이전트 설정</h1>
        <button onClick={() => setAdding(true)}>+ 런타임 추가</button>
      </div>
      <p className={styles.hint}>
        여기서는 AI 런타임(claude/codex/…)을 켜고 끄고, 로그인과 모델/모드 목록만 관리합니다. "이 폴더에서 실제로
        실행되는가"는 워크스페이스 화면에서 폴더별로 확인합니다.
      </p>

      {error && <p className="errorText">{error}</p>}

      {providers.length === 0 && <p className={styles.checkedAt}>등록된 런타임이 없습니다.</p>}
      {providers.map((provider) => (
        <ProviderCard key={provider.id} provider={provider} onChanged={load} onError={setError} />
      ))}

      {adding && (
        <AddProviderModal
          registeredKeys={providers.map((p) => p.key)}
          onClose={() => setAdding(false)}
          onCreated={() => {
            setAdding(false);
            load();
          }}
        />
      )}
    </div>
  );
}
