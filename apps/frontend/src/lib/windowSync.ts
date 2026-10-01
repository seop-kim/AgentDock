/**
 * 에이전트 터미널을 **새 창**으로 띄운다(`/terminal/:agentId`, 사이드바·레이아웃 없는 별도 라우트).
 * 새 창이 그 실행의 로그 스트림(SSE)을 직접 구독하므로, 목업 시절의 스냅샷 다리(BroadcastChannel)는 두지 않는다.
 * 메뉴 클릭 안에서 열어야 팝업 차단에 걸리지 않는다.
 */
export function openTerminalWindow(agentId: number): void {
  window.open(`/terminal/${agentId}`, '_blank', 'width=960,height=640');
}
