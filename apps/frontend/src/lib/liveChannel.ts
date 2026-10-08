import { api } from './api';

/**
 * 전역 이벤트 스트림이 실어 오는 알림. 스토어의 `DataChanged` 와 **구조가 같다** —
 * 스토어가 이 모듈을 쓰게 되므로 순환 import 를 피하려고 여기서 최소 모양만 정의한다.
 */
export interface LiveEvent {
  type: string;
  projectId: number | null;
}

/**
 * 탭 사이에 SSE 를 **하나로 모으는** 채널.
 *
 * <p>브라우저는 오리진당 HTTP/1.1 연결 6개만 허용한다. 탭마다 전역 이벤트 스트림과 실행 로그 스트림을 열면
 * 이 6칸이 다 차고, 그 순간 초기 로딩(`reload`) 요청이 큐에 갇혀 화면이 "불러오는 중"에서 끝나지 않는다.
 *
 * <p>그래서 **Web Locks** 로 탭 중 하나를 리더로 뽑아 그 탭만 SSE 를 열고, 받은 것을
 * **BroadcastChannel** 로 모든 탭에 뿌린다. 리더 탭이 닫히면 락이 풀려 **대기하던 다음 탭이 자동으로 승격**된다
 * (Web Locks 는 문서가 사라지면 브라우저가 알아서 해제하므로 좀비 리더가 남지 않는다).
 *
 * <p>알려진 단순화: 리더가 이미 열어 둔 실행을 나중에 구독한 탭은 **서버 재생(replay)을 받지 못한다**(새 줄부터 본다).
 * 처음 구독한 탭(=스트림을 여는 탭)은 재생을 받는다. 필요해지면 리더에 링 버퍼를 두고 승격 시 재전송하면 된다.
 */

const CHANNEL = 'agentdock-live';
const LOCK = 'agentdock-live-leader';

type LiveMessage =
  | { kind: 'event'; payload: LiveEvent }
  | { kind: 'connection'; connected: boolean }
  | { kind: 'log'; executionId: number; stream: string; text: string }
  | { kind: 'exit'; executionId: number; status: string }
  | { kind: 'want'; executionId: number }
  | { kind: 'drop'; executionId: number }
  | { kind: 'hello' }
  | { kind: 'leader' };

// ── 바깥에 보여 주는 구독 ────────────────────────────────────────────────────────

const eventHandlers = new Set<(event: LiveEvent) => void>();
const connectionHandlers = new Set<(connected: boolean) => void>();
interface LogHandlers {
  onLine: (line: { stream: string; text: string }) => void;
  onExit: () => void;
}
const logHandlers = new Map<number, Set<LogHandlers>>();

/** 전역 데이터 변경 알림 + 실시간 연결 여부. 리더가 열고, 팔로워는 채널로 받는다. */
export function subscribeEvents(handlers: {
  onEvent: (event: LiveEvent) => void;
  onConnection: (connected: boolean) => void;
}): () => void {
  eventHandlers.add(handlers.onEvent);
  connectionHandlers.add(handlers.onConnection);
  // 리더가 이미 연결돼 있으면 지금 상태를 알려 준다(팔로워는 연결이 없어 모른다).
  if (leaderActive) handlers.onConnection(connected);
  return () => {
    eventHandlers.delete(handlers.onEvent);
    connectionHandlers.delete(handlers.onConnection);
  };
}

/** 한 실행의 로그. 구독이 0이 되면 `drop` 을 보내 리더가 그 스트림을 닫는다(연결을 놀리지 않는다). */
export function subscribeLog(executionId: number, handlers: LogHandlers): () => void {
  const set = logHandlers.get(executionId) ?? new Set<LogHandlers>();
  const first = set.size === 0;
  set.add(handlers);
  logHandlers.set(executionId, set);
  if (first) send({ kind: 'want', executionId });
  if (leaderActive) openLog(executionId);
  return () => {
    const current = logHandlers.get(executionId);
    if (!current) return;
    current.delete(handlers);
    if (current.size === 0) {
      logHandlers.delete(executionId);
      send({ kind: 'drop', executionId });
      if (leaderActive) closeLog(executionId);
    }
  };
}

// ── 채널·락 ────────────────────────────────────────────────────────────────────

const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL);
const eventSource = new Map<number, EventSource>();
let globalSource: EventSource | null = null;
let leaderActive = false;
let connected = false;

function send(message: LiveMessage): void {
  channel?.postMessage(message);
}

function broadcast(message: LiveMessage): void {
  // 리더 자신에게는 BroadcastChannel 이 전달하지 않으므로, 보내고 나서 직접 적용한다.
  send(message);
  apply(message);
}

function apply(message: LiveMessage): void {
  switch (message.kind) {
    case 'event':
      eventHandlers.forEach((handler) => handler(message.payload));
      return;
    case 'connection':
      connected = message.connected;
      connectionHandlers.forEach((handler) => handler(message.connected));
      return;
    case 'log':
      logHandlers.get(message.executionId)?.forEach((handler) =>
        handler.onLine({ stream: message.stream, text: message.text }),
      );
      return;
    case 'exit':
      logHandlers.get(message.executionId)?.forEach((handler) => handler.onExit());
      return;
    case 'want':
      openLog(message.executionId);
      return;
    case 'drop':
      closeLog(message.executionId);
      return;
    case 'hello':
      // 새 탭이 상태를 물었다 — 리더만 답한다(팔로워가 답하면 중복이 된다).
      if (leaderActive) {
        send({ kind: 'connection', connected });
        send({ kind: 'leader' });
      }
      return;
    case 'leader':
      // 리더가 바뀌었다 — 내가 보고 있는 로그를 새 리더에게 다시 요청한다(승격한 리더는 상태가 없다).
      logHandlers.forEach((_, executionId) => send({ kind: 'want', executionId }));
      return;
    default:
      return;
  }
}

channel?.addEventListener('message', (event: MessageEvent<LiveMessage>) => apply(event.data));

// ── 리더가 여는 스트림 ───────────────────────────────────────────────────────────

function startGlobalStream(): void {
  const source = new EventSource(`${api.base}/events/stream`);
  source.onopen = () => broadcast({ kind: 'connection', connected: true });
  source.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data as string) as LiveEvent;
      if (typeof (payload as { type?: string })?.type === 'string') {
        broadcast({ kind: 'event', payload });
      }
    } catch {
      // 못 읽은 알림은 무시한다(안전망 폴링이 따라잡는다).
    }
  };
  source.onerror = () => {
    broadcast({ kind: 'connection', connected: false });
    // EventSource 가 스스로 다시 붙는다 — 상태만 알린다.
  };
  globalSource = source;
}

type DataChangeish = { type?: string };

function openLog(executionId: number): void {
  if (!leaderActive || eventSource.has(executionId)) return;
  const source = new EventSource(`${api.base}/executions/${executionId}/stream`);
  source.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data as string) as { stream?: string; content?: string };
      broadcast({
        kind: 'log',
        executionId,
        stream: payload.stream ?? 'stdout',
        text: payload.content ?? '',
      });
    } catch {
      // JSON 이 아니면 무시한다.
    }
  };
  source.addEventListener('exit', () => {
    broadcast({ kind: 'exit', executionId, status: 'exit' });
    source.close();
    eventSource.delete(executionId);
  });
  source.onerror = () => source.close();
  eventSource.set(executionId, source);
}

function closeLog(executionId: number): void {
  const source = eventSource.get(executionId);
  if (!source) return;
  source.close();
  eventSource.delete(executionId);
}

function startLeading(): void {
  if (leaderActive) return;
  leaderActive = true;
  startGlobalStream();
  logHandlers.forEach((_, executionId) => openLog(executionId));
  broadcast({ kind: 'leader' });
}

function stopLeading(): void {
  leaderActive = false;
  globalSource?.close();
  globalSource = null;
  eventSource.forEach((source) => source.close());
  eventSource.clear();
  broadcast({ kind: 'connection', connected: false });
}

/**
 * 탭 중 하나만 리더가 된다. 다른 탭들은 여기서 **대기**하다가 리더가 사라지면 자동으로 승격된다
 * (콜백이 끝나거나 문서가 사라지면 Web Locks 가 락을 풀어 준다).
 */
function electLeader(): void {
  if (!navigator.locks) {
    // Web Locks 가 없으면 예전처럼 이 탭이 직접 연결한다(중복 연결을 감수).
    startLeading();
    return;
  }
  void navigator.locks
    .request(LOCK, { mode: 'exclusive' }, () => {
      startLeading();
      // 리더인 동안 유지한다. 페이지가 사라지면 브라우저가 락을 풀고 다음 탭이 이어받는다.
      return new Promise<void>((resolve) => {
        window.addEventListener('pagehide', () => {
          stopLeading();
          resolve();
        });
      });
    })
    .catch(() => stopLeading());
}

send({ kind: 'hello' });
electLeader();
