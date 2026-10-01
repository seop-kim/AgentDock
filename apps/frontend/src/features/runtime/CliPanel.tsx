import { useEffect, useState } from 'react';
import { CliStatus, WorkspaceRuntime, api } from '../../lib/api';
import CommandPanel, { CommandKind } from './CommandPanel';
import styles from './Providers.module.css';

interface CliPanelProps {
  providerId: number;
  providerName?: string;
  /** 있으면 "이 폴더에서 확인" 을 제공한다. */
  workspaceId?: number;
  /** 설치 명령(없으면 CLI 설치 버튼 비활성). */
  installCommands?: string[] | null;
  onClose: () => void;
  /** 이 폴더에서 확인/설치 후 부모 목록을 갱신할 때. */
  onChecked?: () => void;
}

/**
 * 런타임 CLI 를 확인하는 창. 실행 파일 경로/버전/실행 가능 여부를 보여 주고,
 * 이 폴더에서 확인(probe)·로그인·설치를 한 곳에서 실행하며 출력은 SSE 로 스트리밍한다.
 */
export default function CliPanel({
  providerId,
  providerName,
  workspaceId,
  installCommands,
  onClose,
  onChecked,
}: CliPanelProps) {
  const [status, setStatus] = useState<CliStatus | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [session, setSession] = useState<CommandKind | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<WorkspaceRuntime | null>(null);
  const [checkError, setCheckError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setStatus(null);
    setStatusError(null);
    api
      .getProviderCli(providerId)
      .then((value) => {
        if (!cancelled) setStatus(value);
      })
      .catch((e) => {
        if (!cancelled) setStatusError(String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [providerId]);

  const onCheck = async () => {
    if (workspaceId === undefined) return;
    setChecking(true);
    setCheckError(null);
    try {
      const result = await api.checkWorkspaceRuntime(workspaceId, providerId);
      setCheckResult(result);
      onChecked?.();
    } catch (e) {
      setCheckError(String(e));
    } finally {
      setChecking(false);
    }
  };

  const steps = installCommands ?? [];
  const title = providerName ? `${providerName} CLI` : 'CLI';

  return (
    <div className={styles.loginPanel}>
      <p className={styles.checkedAt}>{title} 상태</p>
      {statusError && <p className="errorText">{statusError}</p>}
      {!status && !statusError && <p className={styles.checkedAt}>CLI 정보를 확인하는 중...</p>}
      {status && (
        <div className={styles.statusRow}>
          <span
            className={status.runnable ? `${styles.badge} ${styles.badgeOk}` : `${styles.badge} ${styles.badgeError}`}
          >
            {status.runnable ? '실행 가능' : '실행 불가'}
          </span>
          <span className={styles.checkedAt}>
            {status.runnable
              ? `경로: ${status.resolvedPath}${status.version ? ` / 버전: ${status.version}` : ''}`
              : (status.detail ?? 'CLI 를 확인할 수 없습니다')}
          </span>
        </div>
      )}
      {status?.runnable && status.detail && <p className={styles.checkedAt}>{status.detail}</p>}

      <div className={styles.actions}>
        {workspaceId !== undefined && (
          <button onClick={onCheck} disabled={checking}>
            {checking ? '확인 중...' : '이 폴더에서 확인'}
          </button>
        )}
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

      {workspaceId === undefined && (
        <p className={styles.checkedAt}>폴더별 상태("이 폴더에서 확인")는 워크스페이스 화면에서 확인하세요.</p>
      )}

      {checkResult && (
        <p className={styles.checkedAt}>
          이 폴더 확인 결과: {checkResult.status}
          {checkResult.lastError ? ` — ${checkResult.lastError}` : ''}
        </p>
      )}
      {checkError && <p className="errorText">{checkError}</p>}

      {session && (
        <CommandPanel
          providerId={providerId}
          kind={session}
          commands={steps}
          onClose={() => setSession(null)}
          onExit={(exitCode) => {
            if (exitCode !== 0) return;
            if (workspaceId !== undefined) onCheck();
            else onChecked?.();
          }}
        />
      )}
    </div>
  );
}
