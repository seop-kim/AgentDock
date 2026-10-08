import { useCallback, useEffect, useState } from 'react';
import { PlusIcon, TerminalIcon } from '../../components/icons';
import { api, type ProviderCatalogEntry, type ProviderCliStatus } from '../../lib/api';
import { useAgentDockStore } from '../../store/AgentDockStore';
import shared from '../../styles/shared.module.css';
import AddRuntimeModal from './AddRuntimeModal';
import RuntimeCard from './RuntimeCard';
import styles from './Settings.module.css';

/**
 * AI 런타임(에이전트 연결) 설정.
 * 목록은 스토어가 들고 있고, **CLI 상태는 서버가 한 번에**(`GET /ai-providers/cli`) 주며,
 * 등록 가능한 런타임과 기본값은 **카탈로그**(`GET /ai-providers/catalog`)에서 온다.
 */
export default function AgentConnectionSettings() {
  const { providers, loading } = useAgentDockStore();
  const [adding, setAdding] = useState(false);
  const [catalog, setCatalog] = useState<ProviderCatalogEntry[]>([]);
  const [cliStatuses, setCliStatuses] = useState<Record<number, ProviderCliStatus>>({});
  const [cliChecking, setCliChecking] = useState(false);

  // 등록 가능한 런타임 카탈로그(구버전 서버면 비어 있고, 추가 창은 기본값으로 내려간다).
  useEffect(() => {
    api.getProviderCatalog().then(setCatalog).catch(() => setCatalog([]));
  }, []);

  // CLI 상태는 한 번에 읽는다(카드마다 `<cli> --version` 을 실행하지 않게).
  const loadCli = useCallback(() => {
    setCliChecking(true);
    api
      .listProviderCli()
      .then((rows) => setCliStatuses(Object.fromEntries(rows.map((row) => [row.providerId, row]))))
      .catch(() => setCliStatuses({}))
      .finally(() => setCliChecking(false));
  }, []);

  useEffect(() => {
    loadCli();
  }, [loadCli, providers.length]);

  /** 카탈로그 기본값을 화면 타입으로 정규화한다(없으면 null — "기본값으로" 버튼을 숨긴다). */
  const defaultCapabilitiesOf = (key: string) => {
    const caps = catalog.find((entry) => entry.key === key)?.capabilities;
    if (!caps) return null;
    return {
      models: caps.models ?? [],
      modes: caps.modes ?? [],
      notes: caps.notes ?? null,
      install: caps.install ?? [],
      installRequire: caps.installRequire ?? null,
      installPrerequisite: caps.installPrerequisite ?? [],
    };
  };

  return (    <div>
      <div className={styles.sectionHeader}>
        <h1>에이전트 연결 설정</h1>
        <div className={shared.statusRow}>
          <button type="button" className={styles.ghostButton} onClick={loadCli} disabled={cliChecking}>
            <TerminalIcon size={15} /> {cliChecking ? 'CLI 확인 중…' : 'CLI 상태'}
          </button>
          <button
            type="button"
            className={styles.iconButton}
            onClick={() => setAdding(true)}
            aria-label="런타임 추가"
            title="런타임 추가"
          >
            <PlusIcon size={18} />
          </button>
        </div>
      </div>
      <p className={shared.hint}>
        여기서는 AI 런타임(claude/codex/…)을 켜고 끄고 이름·로그인·모델/모드를 관리합니다. "이 폴더에서 실제로
        실행되는가"는 워크스페이스 화면에서 폴더별로 확인합니다. 카드의 CLI 줄은 서버가 한 번에 조회한 값입니다.
      </p>

      {loading && <p className={shared.muted}>불러오는 중…</p>}
      {!loading && providers.length === 0 && <p className={shared.muted}>등록된 런타임이 없습니다.</p>}
      {providers.map((provider) => (
        <RuntimeCard
          key={provider.id}
          provider={provider}
          cli={cliStatuses[provider.id] ?? null}
          defaultCapabilities={defaultCapabilitiesOf(provider.key)}
        />
      ))}

      {adding && <AddRuntimeModal onClose={() => setAdding(false)} />}
    </div>
  );
}