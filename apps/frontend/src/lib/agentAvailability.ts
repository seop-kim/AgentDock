import type { Agent, AiProvider, Project } from '../types';

/**
 * 에이전트를 새로 실행할 수 없는 사유(없으면 null).
 * 백엔드가 실행 시점에 판정해 `Agent` 응답에 실어 준 파생값(`available`/`unavailableReason`)을 그대로 옮긴다
 * (런타임 삭제/꺼짐, 프로젝트 기본 워크스페이스에서 런타임 미확인·미연결).
 * 시그니처는 목업 시절과 같게 두어 화면 코드를 바꾸지 않는다(providers·project 는 더 이상 쓰지 않는다).
 */
export function unavailableReason(agent: Agent, _providers: AiProvider[], _project: Project | undefined): string | null {
  if (agent.available) return null;
  switch (agent.unavailableReason) {
    case 'PROVIDER_DELETED':
      return '런타임이 삭제되었습니다';
    case 'RUNTIME_DISABLED':
      return '런타임이 꺼져 있습니다';
    case 'CONNECTION_NOT_STARTED':
      return '이 폴더에서 런타임을 아직 확인하지 않았습니다';
    case 'CONNECTION_NOT_CONNECTED':
      return '이 폴더에서 런타임을 실행할 수 없습니다';
    default:
      return '지금은 실행할 수 없습니다';
  }
}
