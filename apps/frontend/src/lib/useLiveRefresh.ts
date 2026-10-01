import { useEffect } from 'react';

/** 프로젝트 상세·실행 트리·터미널을 다시 읽는 주기. 화면에는 늘 살아 있는 것처럼 보여야 한다. */
export const LIVE_REFRESH_MS = 3000;

/**
 * 화면을 **항상** 주기적으로 다시 읽는다(구성도·에이전트/그룹 카드·채팅·요약 카드가 서로 어긋나지 않게).
 *
 * <p>문서가 숨겨져 있으면(`document.hidden`) 멈추고, 다시 보이는 순간 한 번 읽고 주기를 되살린다 —
 * 안 보는 동안에는 서버를 두드리지 않는다.
 */
export function useLiveRefresh(reload: () => void | Promise<void>, intervalMs: number = LIVE_REFRESH_MS): void {
  useEffect(() => {
    let timer: number | undefined;

    const stop = () => {
      if (timer === undefined) return;
      window.clearInterval(timer);
      timer = undefined;
    };

    const start = () => {
      stop();
      timer = window.setInterval(() => void reload(), intervalMs);
    };

    const sync = () => {
      if (document.hidden) {
        stop();
        return;
      }
      void reload();
      start();
    };

    if (!document.hidden) start();
    document.addEventListener('visibilitychange', sync);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', sync);
    };
  }, [reload, intervalMs]);
}
