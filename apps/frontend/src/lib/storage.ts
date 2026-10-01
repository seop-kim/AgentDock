/**
 * localStorage 에 저장하는 값은 UI 설정(테마, 사이드바 펼침)뿐이다. 서버 데이터는 저장하지 않는다.
 * 저장소가 막혀 있어도(시크릿 모드 등) 화면은 동작해야 하므로 모든 접근을 try/catch 로 감싼다.
 */
export const STORAGE_KEYS = {
  /** index.html 의 초기 적용 스크립트도 같은 키를 쓴다. */
  theme: 'agentdock-theme',
  sidebarExpanded: 'agentdock-sidebar-expanded',
} as const;

export function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeStorage(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 저장 실패는 무시한다(이번 세션에서만 적용).
  }
}
