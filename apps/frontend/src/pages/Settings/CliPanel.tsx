import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import shared from '../../styles/shared.module.css';
import type { AiProvider, CliStatus } from '../../types';
import CommandPanel, { CommandKind } from './CommandPanel';

/**
 * 런타임 CLI 확인 창. 경로/버전/실행 가능 여부를 백엔드(`GET /ai-providers/{id}/cli`)에서 읽고,
 * 로그인·설치 세션(`POST .../login`·`.../install`)을 열어 출력을 `CommandPanel`(SSE)로 보여 준다.
 */
export default function CliPanel({ provider, onClose }: { provider: AiProvider; onClose: () => void }) {
  const [status, setStatus] = useState<CliStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<{ id: string; kind: CommandKind } | null>(null);
  const steps = provider.capabilities.install;

  useEffect(() => {
    let alive = true;
    setStatus(null);
    setError(null);
    api
      .getProviderCli(provider.id)
      .then((result) => {
        if (alive) setStatus(result);
      })
      .catch((ex) => {
        if (alive) setError(`CLI 상태를 확인하지 못했습니다: ${String(ex)}`);
      });
    return () => {
      alive = false;
    };
  }, [provider.id]);

  /** 로그인/설치 세션을 연다(런타임당 활성 세션은 하나). */
  const start = (kind: CommandKind) => {
    setBusy(true);
    setError(null);
    const call = kind === 'login' ? api.startLogin(provider.id) : api.startInstall(provider.id);
    call
      .then((result) => setSession({ id: result.sessionId, kind }))
      .catch((ex) => setError(`${kind === 'login' ? '로그인' : '설치'} 세션을 열지 못했습니다: ${String(ex)}`))
      .finally(() => setBusy(false));
  };

  /** 열어 둔 세션을 닫는다(서버 프로세스를 정리한다). */
  const closeSession = () => {
    if (session !== null) api.stopSession(session.id).catch(() => {});
    setSession(null);
  };

  return (
    <div className={shared.panel}>
      <p className={shared.muted}>{provider.name} CLI 상태</p>
      {!status && error === null && <p className={shared.muted}>CLI 정보를 확인하는 중...</p>}
      {error && <p className="errorText">{error}</p>}
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
        <button onClick={() => start('login')} disabled={busy || session !== null}>
          로그인
        </button>
        <button
          onClick={() => start('install')}
          disabled={busy || session !== null || steps.length === 0}
          title={steps.length === 0 ? '설치 명령이 없습니다. "모델/모드/설치 편집"에서 입력하세요.' : undefined}
        >
          CLI 설치
        </button>
        <button
          onClick={() => {
            closeSession();
            onClose();
          }}
        >
          닫기
        </button>
      </div>

      <p className={shared.muted}>폴더별 상태("이 폴더에서 확인")는 워크스페이스 화면에서 확인하세요.</p>

      {session && <CommandPanel kind={session.kind} sessionId={session.id} onClose={closeSession} />}
    </div>
  );
}
