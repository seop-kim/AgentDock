# 로그 다중화 스트림 계약 — `ExecutionStreamHub.subscribeMany` + `GET /executions/stream?ids=` (2026-10-08)

이 문서는 **계약서**다. 목표는 터미널(실행 로그)을 **한 연결**로 받아 브라우저 오리진당 연결 수를 줄이는 것이다(HANDOVER §15·§16). 구현(백엔드 15/16, 프론트 19/18)은 허브·컨트롤러·프론트를 **한 커밋에서** 끝낸다(§6).

근거: `agents/CONVENTIONS.md`("CLI 출력을 다루는 법" — 구독 시 로그 재생 + 종료 시 `exit`, `SseEmitter(0L)`; "실시간 갱신" — 이벤트 스트림 우선) · `agents/HANDOVER.md` §15(연결 고갈)·§16(리더 탭) · 실측 코드(`execution/service/ExecutionStreamHub.java`, `execution/controller/ExecutionController.java`, `apps/frontend/src/lib/liveChannel.ts`, `components/TerminalView.tsx`).

## 0. 배경 (실측)

- 실행 로그는 `GET /executions/{id}/stream`(SSE). 허브는 실행별 `buckets`(=`Bucket{ emitters, lines }`)를 들고, 구독하면 **지금까지 로그를 재생**하고 이어서 라이브로 보내며, 끝나면 `exit`(`{status, exitCode}`)를 보내고 닫는다(`ExecutionStreamHub`).
- HANDOVER §15: 오리진당 6연결 고갈 → "불러오는 중" 무한 대기. 프론트는 `TerminalView` 의 살아 있는 로그 스트림을 2개로 제한했었다.
- HANDOVER §16: `lib/liveChannel.ts` 로 **탭 간** 중앙화(Web Locks 리더 + BroadcastChannel). 리더 탭 하나만 전역 `/events/stream` 과 **필요한 실행 로그 스트림**을 연다. 그런데 리더는 여전히 **실행마다** `EventSource('/executions/{id}/stream')` 를 하나씩 연다 — 터미널을 여러 개 열면 여전히 N개다.
- 이 계약은 그 **N → 1** 을 만든다. `MAX_LIVE_STREAMS` 제한은 §16 리팩터로 이미 사라졌다(`TerminalView.tsx` 에 없음) — 다시 만들지 않는다.

## 1. 허브 자료구조 변경 — 다중 구독 팬아웃

핵심: **한 emitter 가 여러 실행을 구독**한다. 실행별 `buckets` 는 그대로 두고, 그 emitter 를 **구독한 실행의 bucket 각각에 등록**한다. 그러면 `line(id)` 는 그 실행 bucket 의 모든 구독자(단일 + 다중)에게 자연히 팬아웃된다.

`Bucket.emitters: List<SseEmitter>` 를 `List<Subscriber>` 로 바꾼다. 이유: `close(id)` 에서 구독 하나가 단일인지 다중인지 알아야 한다(다중은 그 id 만 떼고 연결 유지, 단일은 연결을 닫는다).

```java
final class Subscriber {
    final SseEmitter emitter;
    final boolean multiplexed;                               // subscribe = false, subscribeMany = true
    final Set<String> keys = ConcurrentHashMap.newKeySet();  // 이 emitter 가 붙은 실행 key(String.valueOf(id))
}
```
- `subscribe(Long id)` — 기존 동작 유지. `Subscriber(multiplexed=false)` 를 그 실행 bucket 에 등록(끝난 실행이면 DB 재생 + `exit` + `complete`).
- `subscribeMany(Collection<Long> ids)` — 새 메서드.
  1. `ids` 정규화: null 제거, 중복 제거, 순서 유지. (빈 목록은 컨트롤러가 400 — §4.)
  2. `new SseEmitter(0L)`; 한 개의 `Subscriber(multiplexed=true)` 를 만든다.
  3. 각 id 별로:
     - 라이브 bucket 있음 → 버퍼 재생(dropped 안내 + 줄들) → 그 bucket 에 Subscriber 등록 + `keys` 추가.
     - 라이브 bucket 없음(끝났거나 시작 전) → DB 로그(`REPLAY_LIMIT` 500줄) 재생 → 그 id 의 `exit` 즉시 전송 → **등록하지 않음**.
  4. 등록된 key 가 하나도 없으면(전부 끝남/모름) `complete()`.
  5. `onCompletion`/`onTimeout`/`onError` = **구독 해제 정리**: `keys` 의 모든 bucket 에서 이 Subscriber 를 제거하고 emitter 완료. bucket 은 `close` 로 사라질 수 있으니 "있으면 제거"로 관대하게.
- `line(id, ...)` — payload 에 `executionId` 를 더한 뒤(§3) bucket 의 **모든** Subscriber 에게 전송(단일/다중 동시 팬아웃). 전송 실패 시 그 Subscriber 를 **모든** bucket 에서 정리.
- `close(id, status, exitCode)` — 기존처럼 bucket 을 제거하고 각 Subscriber 에게 `exit(id,status,exitCode)` 전송 후:
  - 단일(`multiplexed=false`) → `complete()`(기존과 동일).
  - 다중(`multiplexed=true`) → `keys` 에서 이 id 제거. `keys` 가 비면 `complete()`, 아니면 **연결 유지**(다른 실행 로그를 계속 받는다 — 다중화의 핵심).
- 기존 단일 `subscribe` 의 동작·exit payload 규약은 그대로(내부만 Subscriber 로 감싼다).

## 2. 컨트롤러

```
GET /executions/stream?ids=1,2
```

- `@GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)`; `@RequestParam String ids` 를 **수동 파싱**(콤마) → `List<Long>` → `ExecutionService.streamMany(list)` → `hub.subscribeMany`.
- 라우팅 주의: `GET /executions/{id}`(id=Long)와 겹칠 수 있으나 Spring 은 **리터럴 `/stream` 세그먼트를 변수보다 우선** 매칭한다 → `/executions/stream` 는 이 핸들러로 간다(회귀 테스트로 못 박는다 — §6).
- 기존 `GET /executions/{id}/stream` **유지**(삭제 금지). 새 엔드포인트는 추가 전용.
- 반환 `SseEmitter`(`new SseEmitter(0L)`).
## 3. 이벤트 규약 (payload 통일 — 추가 전용)

- 로그 줄(기본 `message`): `{"executionId":12,"stream":"stdout","content":"..."}`.
  - 기존 단일 스트림도 같은 payload 를 쓴다(필드 **추가** — 하위호환. 프론트는 `stream`/`content` 만 읽고 `executionId` 는 무시).
- 종료: `event: exit`, `data: {"executionId":12,"status":"SUCCEEDED","exitCode":0}`.
  - 다중은 실행별로 각각 보낸다. `executionId` 로 어느 실행이 끝났는지 구분 — 다중화에서 필수(단일엔 잉여지만 무해).
  - `status` 는 `ExecutionStatus`, `exitCode` 없으면 `-1`(기존 규칙).
- 이미 끝난 실행을 구독하면 DB 로그(최대 500줄) 재생 후 그 id 의 `exit` 즉시, 연결은 다른 실행이 살아 있으면 **유지**.
- 버퍼 넘침 안내(`⎿ 이전 로그 N줄 …`)는 실행별로 그대로.
- **하트비트 없음(이번 커밋 범위 밖)**: 기존 단일 스트림과 동일하게 로그 흐름에 의존한다. 유휴 장기 연결 문제가 실측되면 단일·다중 **둘 다**에 25초 주석 줄을 다음 커밋에서 넣는다(한쪽만 넣지 않는다).

## 4. 오류/입력 규칙

| 경우 | 상태 | 본문/동작 |
| --- | --- | --- |
| `ids` 누락·빈 문자열(`?ids=`) | **400** | `ids 는 쉼표로 구분한 실행 id 목록이어야 합니다` |
| 숫자 아닌 원소 | **400** | `잘못된 실행 id: <값>` |
| id 개수 > **20** | **400** | `한 번에 구독할 수 있는 실행은 20개까지입니다` |
| 존재하지 않는 id | (무시) | 이벤트 없음(그 id 의 `exit` 도 보내지 않음). 오래된 탭의 죽은 id 하나가 전체 스트림을 깨면 안 된다. |
| 유효 id 전부 이미 끝남 | **200** | 로그 재생 + 각 `exit` 후 연결 완료 |

- 중복 id 는 한 번만 구독(정규화). 4xx 는 서버 오류 링버퍼에 담지 않는다.
## 5. 프론트 소비 규칙 — 연결 1개로 합치기

- `lib/liveChannel.ts` 의 `openLog(id)`/`eventSource: Map<number,EventSource>` 를 **단일 다중 스트림**으로 교체:
  - 리더는 원하는 실행 id **집합**(`logHandlers` 의 key)을 모아 `EventSource(api.base + '/executions/stream?ids=' + [...ids].join(','))` **하나**를 연다.
  - 수신: `onmessage` 의 `payload.executionId` 로 `logHandlers.get(executionId)` 에 라우팅. `exit` 리스너는 `payload.executionId` 의 구독자에게만 `onExit`.
  - id 집합이 바뀌면(want/drop) **디바운스 후 재연결**(예: 100ms) — 실행 수명 동안 잦은 want/drop 로 연결을 계속 열고 닫지 않는다. 집합이 비면 닫는다.
  - 리더 교체(`leader`/`hello`) 시 새 리더가 자기 집합으로 다시 연다(기존 로직 그대로, `openLog` 만 다중 버전으로 교체).
  - 리더 링버퍼(`buffers`, `replay`)·`want`/`drop` 참조 카운트는 그대로 재사용(로그는 `remember` 로 실행별 최근 400줄 유지).
- `components/TerminalView.tsx` 는 `subscribeLog(id, ...)` 만 쓰므로 **변경 없음**. `MAX_LIVE_STREAMS` 는 이미 없음.
- 폴백: Web Locks 가 없으면 탭이 직접 다중 스트림을 연다(기존 폴백과 동일).
- 회귀: 초기 로딩(`reload`)의 6연결 고갈이 다시 나면 안 된다 — 리더의 SSE 는 **전역 1 + 로그 최대 1** 이어야 한다(§15 증상 재발 금지).

## 6. 한 커밋에서 끝낼 범위 (중요 — 반쯤 바꾸지 않는다)

이유: 허브는 실시간 전체가 걸려 있어 반쯤 바꾸면 터미널 로그가 끊긴다(HANDOVER §15 step 4). 다음은 **한 커밋(Commit A)** 에 함께 들어간다:

- 백엔드: `execution/service/ExecutionStreamHub`(Subscriber·`subscribeMany`·payload `executionId`·다중 `close` 정리) · `execution/service/ExecutionService`(+`streamMany`) · `execution/controller/ExecutionController`(+`GET /executions/stream`) · 테스트(`ExecutionStreamHubTest` 확장: 다중 팬아웃, 한 실행 close 후 다른 실행 유지, 구독 해제 정리, 이미 끝난 실행 exit, 리터럴 라우팅).
- 프론트: `lib/liveChannel.ts`(실행별 EventSource → 단일 다중 EventSource), 필요하면 `lib/api.ts` 의 스트림 URL 헬퍼. `components/TerminalView.tsx` 는 구독 API 가 같아 변경 없음을 확인.
- 문서: 이 계약서 + `agents/HANDOVER.md`(§15 다음 항목 완료 표시) + `agents/CONVENTIONS.md`(실행 로그 SSE 문장에 다중 엔드포인트 추가).
- **하지 않음(같은 커밋에서 손대지 않음)**: `GET /executions/{id}/stream` 의 동작·payload 규약(유지) · `EventStreamService`(전역 이벤트) · 실행 목록 API(Commit B) · DB 스키마(변경 없음) · 하트비트(다음 커밋).
- 검증: `.\gradlew.bat test` · `npm run build --workspace=apps/frontend` · 8081/3031 실측 — 터미널 2개 이상을 열어 리더 탭의 SSE 가 **전역 1 + 로그 1** 인지, 한 실행이 끝나도 다른 터미널 로그가 계속 흐르는지, 늦게 연 터미널이 재생을 받는지.

## 7. 하위호환 요약

- `GET /executions/{id}/stream` — **그대로**. payload 에 `executionId` 만 추가(무해). 구버전 프론트는 계속 이 주소를 써도 된다.
- `GET /executions/stream?ids=` — **추가**. 서버는 두 엔드포인트를 모두 제공한다.
- 프론트는 같은 커밋에서 다중으로 전환하되, 전환 직전에도 단일이 살아 있어 안전하다.
