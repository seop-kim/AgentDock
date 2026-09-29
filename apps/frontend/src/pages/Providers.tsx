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
        <h1>Agent 연결 설정</h1>
        <button onClick={() => setAdding(true)}>+ Provider 추가</button>
      </div>
      <p className={styles.hint}>
        CLI 로그인 세션을 그대로 사용합니다(자격증명은 이 앱에 저장하지 않습니다). "연결"은 해당 CLI를 한 번 실행해 실제로
        응답하는지 확인합니다. 연결이 실패하면 "로그인"으로 이 화면에서 바로 로그인할 수 있습니다.
      </p>

      {error && <p className="errorText">{error}</p>}

      {providers.length === 0 && <p className={styles.checkedAt}>등록된 Provider가 없습니다.</p>}
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
