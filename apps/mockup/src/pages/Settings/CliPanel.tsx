import { useEffect, useState } from 'react';
import { CLI_STATUS_BY_KEY, CLI_STATUS_UNKNOWN } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import type { AiProvider, CliStatus } from '../../types';
import CommandPanel, { CommandKind } from './CommandPanel';

const STATUS_DELAY_MS = 600;

/** 런타임 CLI 확인 창. 경로/버전/실행 가능 여부를 보여 주고 로그인·설치를 실행한다(모두 가짜 데이터). */
export default function CliPanel({ provider, onClose }: { provider: AiProvider; onClose: () => void }) {
  const [status, setStatus] = useState<CliStatus | null>(null);
  const [session, setSession] = useState<CommandKind | null>(null);
  const steps = provider.capabilities.install;

  useEffect(() => {
    setStatus(null);
    const timer = window.setTimeout(
      () => setStatus(CLI_STATUS_BY_KEY[provider.key] ?? CLI_STATUS_UNKNOWN),
      STATUS_DELAY_MS,
    );
    return () => window.clearTimeout(timer);
  }, [provider.key]);

  return (
    <div className={shared.panel}>
      <p className={shared.muted}>{provider.name} CLI 상태</p>
      {!status && <p className={shared.muted}>CLI 정보를 확인하는 중...</p>}
      {status && (
        <div className={shared.statusRow}>
          <span className={`${shared.badge} ${status.runnable ? shared.badgeOk : shared.badgeError}`}>
            {status.runnable ? '실행 가능' : '실행 불가'}
          </span>
          <span className={shared.muted}>
            {status.runnable
              ? `경로: ${status.resolvedPath}${status.version ? ` / 버전: ${status.version}` : ''}`
              : (status.detail ?? 'CLI 를 확인할 수 없습니다')}
          </span>
        </div>
      )}

      <div className={shared.actions}>
        <button onClick={() => setSession('login')} disabled={session !== null}>
          로그인
        </button>
        <button
          onClick={() => setSession('install')}
          disabled={session !== null || steps.length === 0}
          title={steps.length === 0 ? '설치 명령이 없습니다. "모델/모드/설치 편집"에서 입력하세요.' : undefined}
        >
          CLI 설치
        </button>
        <button onClick={onClose}>닫기</button>
      </div>

      <p className={shared.muted}>폴더별 상태("이 폴더에서 확인")는 워크스페이스 화면에서 확인하세요.</p>

      {session && <CommandPanel kind={session} commands={steps} onClose={() => setSession(null)} />}
    </div>
  );
}
