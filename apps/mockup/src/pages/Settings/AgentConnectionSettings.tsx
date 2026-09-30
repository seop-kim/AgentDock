import { useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import AddRuntimeModal from './AddRuntimeModal';
import RuntimeCard from './RuntimeCard';
import styles from './Settings.module.css';

export default function AgentConnectionSettings() {
  const { providers } = useMockStore();
  const [adding, setAdding] = useState(false);

  return (
    <div>
      <div className={styles.sectionHeader}>
        <h1>에이전트 연결 설정</h1>
        <button onClick={() => setAdding(true)}>+ 런타임 추가</button>
      </div>
      <p className={shared.hint}>
        여기서는 AI 런타임(claude/codex/…)을 켜고 끄고, 로그인과 모델/모드 목록만 관리합니다. "이 폴더에서 실제로
        실행되는가"는 워크스페이스 화면에서 폴더별로 확인합니다.
      </p>

      {providers.length === 0 && <p className={shared.muted}>등록된 런타임이 없습니다.</p>}
      {providers.map((provider) => (
        <RuntimeCard key={provider.id} provider={provider} />
      ))}

      {adding && <AddRuntimeModal onClose={() => setAdding(false)} />}
    </div>
  );
}
