import { useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import { subscribeLog } from '../lib/liveChannel';
import { logLineKind, type TerminalLine } from '../lib/terminal';
import filter from '../styles/logFilter.module.css';
import term from '../styles/terminal.module.css';

/**
 * 터미널 몸통(실행 출력). 그 실행의 **로그**(SSE)를 구독한다.
 *
 * <p>연결은 **탭 중 리더 하나만** 연다(`lib/liveChannel` — Web Locks + BroadcastChannel). 이 컴포넌트는
 * 구독만 하고, 구독이 0이 되면 리더가 그 실행의 스트림을 닫는다 — 탭마다·실행마다 연결을 열어
 * 브라우저 연결(오리진당 6개)이 차던 문제를 없앤다.
 *
 * <p>`trackTail` 이면 새 줄이 와도 **사용자가 위로 스크롤해 둔 동안에는 따라 내려가지 않는다**(맨 아래일 때만 따라간다).
 * 노드 위 hover 미리보기처럼 안에서 스크롤해 읽는 곳에서 쓴다.
 */
export default function TerminalView({
  executionId,
  live: storeLive,
  className,
  trackTail = false,
  filterable = false,
  history = false,
}: {
  /** 보여 줄 실행. null 이면 아직 실행이 없다(안내 문구만). */
  executionId: number | null;
  /** 스토어가 본 "돌고 있는가". `exit` 이벤트가 오면 그 뒤로는 멈춘다. */
  live: boolean;
  /** 높이 같은 바깥 모양만 바꾼다 */
  className?: string;
  /** true 면 맨 아래에 있을 때만 새 줄을 따라 내려간다(스크롤해 읽는 미리보기용). */
  trackTail?: boolean;
  /** true 면 위에 검색줄을 붙인다(긴 로그를 훑는 터미널 창용). */
  filterable?: boolean;
  /**
   * true 면 **과거 로그**를 한 번만 읽어 정적으로 보여 준다(SSE 구독 없음).
   * 끝난 실행은 서버의 메모리 재생 버퍼가 없을 수 있어 `GET /executions/{id}/logs` 로 DB 에서 읽는다.
   */
  history?: boolean;
}) {
  const [lines, setLines] = useState<TerminalLine[]>([]);
  const [finished, setFinished] = useState(false);
  const [query, setQuery] = useState('');
  const logRef = useRef<HTMLOListElement>(null);
  // 사용자가 맨 아래(여유 24px 이내)에 있는지. trackTail 일 때만 본다.
  const atBottomRef = useRef(true);

  // 실행이 바뀌면 로그를 처음부터 다시 받는다.
  useEffect(() => {
    setLines([]);
    setFinished(false);
    atBottomRef.current = true;
  }, [executionId]);

  // 과거 로그는 한 번만 읽는다(정적). 라이브는 아래 스트림 구독이 맡는다.
  useEffect(() => {
    if (executionId === null || !history) return;
    let alive = true;
    void api
      .getExecutionLogs(executionId)
      .then((rows) => {
        if (!alive) return;
        setLines(rows.map((row) => ({ text: row.content, kind: logLineKind(row.stream) })));
        setFinished(true);
      })
      .catch(() => {
        // 못 읽으면 빈 화면 + 안내 문구를 그대로 둔다.
      });
    return () => {
      alive = false;
    };
  }, [executionId, history]);

  useEffect(() => {
    if (executionId === null || history) return;
    // 리더 탭이 이 실행의 스트림을 열고(이미 열려 있으면 재사용) 줄을 채널로 보내 준다.
    return subscribeLog(executionId, {
      onLine: (line) =>
        setLines((prev) => [...prev, { text: line.text, kind: logLineKind(line.stream) }]),
      // 늦게 구독했거나 리더가 바뀐 경우 — 리더가 최근 줄을 보내 주면 그걸로 다시 채운다.
      onReplay: (lines) =>
        setLines(lines.map((line) => ({ text: line.text, kind: logLineKind(line.stream) }))),
      onExit: () => setFinished(true),
    });
  }, [executionId]);

  useEffect(() => {
    const el = logRef.current;
    if (!el) return;
    // trackTail 이면 사용자가 위로 스크롤해 둔 동안에는 따라 내려가지 않는다.
    if (!trackTail || atBottomRef.current) el.scrollTop = el.scrollHeight;
  }, [lines.length, trackTail]);

  const live = !history && storeLive && !finished;
  const rows: TerminalLine[] =
    lines.length > 0
      ? lines
      : [
          {
            text:
              executionId === null
                ? '지금 실행 중인 작업이 없습니다.'
                : history
                  ? '이 실행에는 저장된 로그가 없습니다.'
                  : '출력을 기다리는 중…',
            kind: 'muted',
          },
        ];

  // 검색어는 화면에 보이는 줄만 거른다(로그 자체는 그대로 받아 둔다 — 지우면 다시 못 본다).
  const needle = query.trim().toLowerCase();
  const visible = needle === '' ? rows : rows.filter((line) => line.text.toLowerCase().includes(needle));
  return (
    <>
      {filterable && (
        <div className={filter.filterBar}>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="로그 검색 (예: error, 실행 #42)"
            aria-label="로그 검색"
          />
          <span className={filter.filterCount}>
            {visible.length} / {rows.length}
          </span>
        </div>
      )}
      <ol
        ref={logRef}
        className={`${term.terminal} ${className ?? ''}`}
        // trackTail 일 때만 스크롤 위치를 본다(미리보기에서 위로 올려 읽는 중인지).
        onScroll={
          trackTail
            ? () => {
                const el = logRef.current;
                if (el) atBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
              }
            : undefined
        }
      >
        {visible.map((line, index) => (
          <li key={`${index}-${line.text}`} className={`${term.line} ${term[line.kind]}`}>
            {line.text}
            {live && index === visible.length - 1 && <span className={term.cursor} />}
          </li>
        ))}
      </ol>
    </>
  );
}
