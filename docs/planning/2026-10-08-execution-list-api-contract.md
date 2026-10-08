# 실행 목록 API 계약 — `GET /executions?projectId=&status=` (2026-10-08)

이 문서는 **계약서**다. 구현(백엔드 16/15, 프론트 18/19)은 이 문서의 규칙을 그대로 따른다. 화면의 정보구조(Tasks 목록이 무엇을 어떤 계층으로 보여 줄지)는 데이터 모델 계약(실행 231)을 따르고, **이 문서는 API 의 요청/응답/오류 규칙과 프론트 소비 규칙**을 못 박는다.

근거: `agents/CONVENTIONS.md`(실시간 갱신은 이벤트 스트림 우선, 바뀐 조각만 REST 재조회) · `agents/HANDOVER.md` §11(프로젝트 단위 대기 조회 API 필요) · `docs/planning/2026-10-08-frontend-data-wiring.md`(없는 API 2·3) · 실측 코드(`execution/`, `AgentDockStore.tsx`, `lib/api.ts`).

## 0. 실측 사실 (설계의 제약 — 추측 아님)

- `execution` 테이블/엔티티에는 **`project_id` 컬럼이 없다**(`execution/domain/Execution.java`). 프로젝트는 오직 `execution.task_id → task.project_id` 로 닿는다. `Execution` 은 "task 패키지 의존을 피하려고" Task 를 관계로 매핑하지 않고 `task_id` 컬럼만 둔다.
- 모든 실행은 생성 시 `taskId` 를 받는다(`ExecutionService.startRoot` → `ExecutionFactory.createRoot/createChild`, 호출부 `DelegationService.start(..., taskId)` ← `TaskService.command`). 즉 현재 흐름에서 `task_id IS NULL` 인 실행은 생기지 않는다(스키마상 nullable 이라 방어는 한다).
- 프론트 `Execution` 타입의 `projectId` 는 **API 응답에 없다** — 스토어가 트리 조회 때 주입한다(`AgentDockStore.tsx` 의 `executionOf(execution, projectId)`). 그래서 실행 목록을 제대로 쓰려면 서버가 `projectId` 를 실어 줘야 한다.
- `ExecutionResponse`(`execution/dto/ExecutionResponse.java`)에는 `projectId`·`taskTitle`·`agentName` 이 없다. `GET /executions/{id}`(상세)와 `GET /executions/{id}/tree`(트리)가 이 DTO 를 쓴다.
- 목록 API 선례: `GET /tasks?projectId=&groupId=&limit=&beforeId=` 는 **봉투(envelope) 없는 배열**을 주고, `limit` 미지정이면 전부, `beforeId` 는 **그보다 오래된 것**만(커서), 정렬은 `id desc` 다(`task/controller/TaskController.java`, `task/service/TaskService.findPage`).

## 1. 엔드포인트

```
GET /executions?projectId=&status=&limit=&beforeId=
```

- 인증 없음(다른 목록 API 와 동일).
- `produces: application/json`. 응답 본문은 **`ExecutionSummaryResponse[]`(봉투 없는 배열)** — `/tasks` 와 같은 패턴. `hasMore` 필드는 두지 않는다(§4).
- 파라미터

| 이름 | 타입 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- | --- |
| `projectId` | Long | 아니오 | 없음(=전 프로젝트) | 그 프로젝트 소속 실행만 |
| `status` | 문자열 1개 또는 목록 | 아니오 | 없음(=전 상태) | `ExecutionStatus` 값. 다중은 쉼표/반복(§2) |
| `limit` | int | 아니오 | **100** | 1..500. 범위 밖 400 (§4) |
| `beforeId` | Long | 아니오 | 없음 | 그 id 보다 **작은(오래된)** 실행만 (커서) |
## 2. 필터

### `projectId` (선택)
- 없으면 전 프로젝트. 있으면 `execution.task_id → task.project_id` 로 판별한 그 프로젝트 실행만.
- **`task_id` 가 NULL 인 실행은 `projectId` 필터에서 제외**한다(현재 흐름에서는 발생하지 않음 — §0).
- 존재하지 않는 프로젝트 id → **404**(§6). 필터 값이 숫자가 아니면 **400**.

### `status` (선택, 다중 허용)
- 단일: `status=RUNNING`.
- 다중: `status=RUNNING,WAITING_CHILD`(쉼표) **또는** `status=RUNNING&status=FAILED`(반복). 둘 다 허용하며 **합집합(OR)** 이다.
- 받는 값은 서버 `ExecutionStatus` 7종 **그대로**만:
  `PENDING` · `RUNNING` · `WAITING_CHILD` · `WAITING_INPUT` · `SUCCEEDED` · `FAILED` · `CANCELLED`.
  프론트가 화면에서 쓰는 별칭(`QUEUED`/`DONE`)은 **받지 않는다**(서버 값 그대로; `QUEUED→PENDING`, `DONE→SUCCEEDED`, `CANCELLED` 는 취소로 표시하는 매핑은 프론트 몫, 기존 `AgentDockStore.executionStatus`).
- 빈 값(`status=`)은 **무시**(= 필터 없음). 알 수 없는 값이 하나라도 있으면 **400**.
- `status` 미지정 → **전 상태**.

### 조합
- `projectId` 와 `status` 는 **AND**.
- 요청 창(내가 처리할 요청)은 `GET /executions?projectId=<열린 프로젝트>&limit=100` 으로 읽고, 화면이 `status === WAITING_INPUT`(질문) 과 `mergeStatus === 'MANUAL'`(수동 병합) 를 **클라이언트에서** 추린다(§7). 수동 병합 축은 status 로 표현되지 않으므로(둘 다 SUCCEEDED 등) 별도 파라미터를 만들지 않는다 — 실행량이 커지면 `mergeStatus` 파라미터를 다음에 추가한다(이 계약 밖).

## 3. 정렬

- **기본 `id DESC`(최신 먼저), 보조 정렬 없음.** 근거: `/tasks` 와 동일, `id` 는 유일·단조 증가라 커서(`beforeId`)에 안전하다.
- 루트(명령) 실행과 자식(위임) 실행이 **같은 축에 섞여** 나온다. 트리 묶기는 응답의 `rootExecutionId`/`parentExecutionId` 로 화면이 한다(§5).
- `createdAt` 대신 `id` 를 쓰는 이유: 생성 순서와 일치하고(IDENTITY) 유일해서 `beforeId` 커서가 중복/누락 없이 동작한다.

## 4. 페이지네이션 (커서)

- `limit`: **기본 100**, 허용 범위 **1..500**. 범위 밖 → **400**. (`/tasks` 처럼 무제한 기본값을 두지 않는다 — 실행은 트리당 최대 20건 × 트리 수로 빠르게 커진다.)
- `beforeId`: 그보다 오래된 실행만. "더 보기" 는 마지막 행의 `id` 를 `beforeId` 로 넘긴다: `?limit=100&beforeId=1234`.
- 응답은 배열 하나. 클라이언트는 `받은 개수 === limit` 이면 더 있을 수 있다고 본다(별도 `hasMore` 없음, `/tasks` 패턴).
- id desc 라 **커서 페이징에서 중복/누락이 없다**.
## 5. 응답 DTO — **기존 `ExecutionResponse` 를 재사용하지 않는다 (못 박음)**

새 lean record **`com.agent.dock.execution.dto.ExecutionSummaryResponse`** 를 만든다.

이유:
1. `ExecutionResponse` 에 **`projectId` 가 없어** 대시보드 집계를 프로젝트로 묶을 수 없다(목록의 목적 자체가 프로젝트/전역 집계와 요청 조회).
2. 목록은 `prompt`·`resultText` 같은 **큰 텍스트가 필요 없다**(대시보드가 다수 행을 읽는다).
3. 목록이 필요한 라벨(`taskTitle`·`agentName`·`groupName`)이 `ExecutionResponse` 에 없다.
4. `ExecutionResponse` 는 상세(`GET /executions/{id}`)·트리(`GET /executions/{id}/tree`)의 계약이다 — **이번 커밋에서 한 글자도 바꾸지 않는다**(하위호환 유지).

필드(정확):

```java
Long id,
Long projectId,            // task.project_id 에서 파생 (taskId null 이면 null)
Long taskId,
String taskTitle,          // task.title (taskId null 이면 null)
Long agentId,
String agentName,          // agent.name
Long groupId,              // task.group_id (그룹 Task 일 때만; 아니면 null)
String groupName,
Long rootExecutionId,      // 루트면 null
Long parentExecutionId,    // 루트면 null
ExecutionStatus status,    // 서버 enum 그대로
ExecutionDecision decision,
Long delegatedTargetAgentId,
String question,           // WAITING_INPUT 질문 (요청 창)
List<String> questionOptions,
String worktreeBranch,
String resultCommit,
MergeStatus mergeStatus,   // MANUAL 판정(요청 창)
String mergeDetail,
BigDecimal costUsd,        // 집계
Integer inputTokens, Integer outputTokens,
Integer cacheReadTokens, Integer cacheCreationTokens,
Long durationMs, Integer numTurns,
Integer exitCode,
Instant startedAt, Instant finishedAt,
Instant createdAt, Instant updatedAt
```

- **포함하지 않음**: `prompt`, `resultText`, `handoff`, `sessionId`(목록에 불필요·용량 큼). 필요하면 상세/트리로 읽는다.
- `status` 는 서버 enum 그대로 싣는다(프론트가 접는 매핑은 이 계약 밖).
- `projectId`/`taskTitle`/`agentName`/`groupName` 파생 구현(재량, 단 §0 제약 유지):
  - (권장) `ExecutionRepository` 에 JPQL **엔티티 조인 + 생성자 프로젝션** — `join Agent a on a.id = e.agentId`, `left join Task t on t.id = e.taskId`, `left join AgentGroup g on g.id = t.groupId`. JPQL 문자열이라 Task/AgentGroup 엔티티 **관계를 새로 만들지 않아도** 된다(Execution 의 "task 패키지 의존 회피" 주석 유지).
  - 또는 QueryDSL(스택에 이미 있음)로 동적 필터.
  - 또는 `execution.project_id` **V15 비정규화**(추가 전용). 이 경우 인덱스 초안·컬럼은 데이터 모델 계약(실행 231)과 **반드시 일치**시킨다. 비정규화를 하지 않으면 **마이그레이션이 없다**.
- 응답 배열의 순서는 §3 정렬을 그대로.
## 6. 오류 규칙 (400 / 404)

| 경우 | 상태 | 본문(예) |
| --- | --- | --- |
| `projectId` 가 숫자가 아님 | **400** | `Invalid path parameter: projectId` (기존 `MethodArgumentTypeMismatchException` 핸들러) |
| `status` 에 `ExecutionStatus` 가 아닌 값 | **400** | `알 수 없는 실행 상태입니다: <값>` (`BadRequestException`) |
| `limit` 이 1..500 밖 | **400** | `limit 은 1..500 입니다` (`BadRequestException`) |
| `projectId` 가 존재하지 않음 | **404** | `Project <id> not found` (`NotFoundException`) |
| 유효한 필터지만 결과 0건 | **200** | `[]` (404 아님) |
| 그 외 | **200** | 배열 |

- 4xx(검증/입력)는 서버 오류 링버퍼(`ServerErrorLog`)에 담지 않는다 — `GlobalExceptionHandler` 의 기존 규칙.
- `beforeId` 가 숫자가 아니면 400(타입 미스매치 핸들러), 존재하지 않는 id 여도 400/404 가 아니라 **200 []**(커서는 단지 하한일 뿐이다).

## 7. 프론트 소비 규칙 — 전역 이벤트 `execution.changed` → **조각만 재조회**

전역 이벤트는 **페이로드가 아니라 알림**이다(`event/dto/DataChangedEvent` — `{type, projectId, executionId, taskId}`). 화면은 바뀐 조각만 기존 REST 로 다시 읽는다. 실행 목록을 쓰는 화면(대시보드·Tasks/실행 목록·요청 창)의 규칙:

1. `execution.changed` 를 받으면 이벤트의 `projectId` 가 **그 화면의 필터와 맞을 때만** 같은 필터로 `GET /executions?...` 를 다시 읽는다. 다르면 무시.
2. 응답 배열로 그 화면의 실행 slice 를 **통째 교체**한다(목록은 트리 단위 병합이 필요 없다 — `mergeExecutions` 같은 부분 병합을 쓰지 않는다).
3. **디바운스**: 스토어의 기존 `pendingRef`(키 `type:projectId`)로 ~250ms 모아 한 번만 처리한다. 같은 조각의 중복 이벤트는 하나로 접힌다.
4. 로그 스트림은 `execution.changed` 로 **재구독하지 않는다**. 상태 변경은 목록/스토어만 갱신하고, 로그는 로그 스트림이 담당한다(계약: `docs/planning/2026-10-08-execution-log-multiplex-stream-contract.md`).
5. **안전망 폴링**은 유지한다(30초, 끊김 시 5초). 이벤트를 놓쳐도 따라잡는다(기존).
6. 프로젝트 상세처럼 트리를 이미 읽는 화면은 기존 `loadProjectSlice(event.projectId, true)` 를 그대로 쓸 수 있다 — **목록 화면만** 위 1~3 으로 추가한다.

결과:
- 대시보드 집계가 화면이 들고 있는 실행만 합산하던 한계(전체가 아님)가 사라진다 — `projectId` 없이 열려 있으면 `reload` 대신 `GET /executions?limit=<n>` 한 조각만 다시 읽는다.
- 요청 창은 `GET /executions?projectId=<열린 프로젝트>&limit=100` 을 읽어 `status===WAITING_INPUT` 과 `mergeStatus==='MANUAL'` 을 추린다 → **오래된/지난 트리에서 물어본 질문도 목록에 뜬다**(HANDOVER §11 한계 해소).
## 8. 한 커밋에서 끝낼 범위 (변경 범위 — 커밋 단위)

이 API 는 **추가 전용(additive)** 이다 — 백엔드를 먼저 올려도 기존 화면이 깨지지 않는다(기존 엔드포인트·DTO 를 건드리지 않는다). 그래서 로그 다중화(별 커밋 A)와 달리 프론트와 커밋을 나눠도 안전하다. 다만 **화면이 실제로 소비하는 배선까지 같은 PR 로 묶는다**.

**Commit B — 실행 목록 API (한 커밋)**
- 백엔드: `execution/repository/ExecutionRepository`(+목록 쿼리) · `execution/dto/ExecutionSummaryResponse`(신규) · `execution/service/ExecutionService`(+`listSummaries(projectId, statuses, limit, beforeId)`) · `execution/controller/ExecutionController`(+`@GetMapping` on `/executions`, `@RequestParam`) · 테스트(정렬·필터·다중 status·커서·400/404·projectId 파생).
- 프론트(같은 PR): `lib/api.ts`(+`listExecutions`) · 소비 화면(대시보드·Tasks/실행 목록·요청 창)의 §7 재조회 배선.
- **하지 않음(같은 커밋에서 손대지 않음)**: `ExecutionResponse`/상세/트리 계약, DB 스키마(비정규화를 안 하면 마이그레이션 없음), 로그 다중화(Commit A), `mergeStatus` 파라미터.
- 검증: `.\gradlew.bat test` · `npm run build --workspace=apps/frontend` · 8081/3031 실측(필터·커서·이벤트 후 재조회).

## 9. 예시

```
GET /executions?projectId=6&limit=2
200 [ {id:104, projectId:6, taskId:39, taskTitle:"연결 확인", agentId:13, agentName:"총괄",
       status:"SUCCEEDED", rootExecutionId:null, parentExecutionId:null, mergeStatus:"MERGED",
       resultCommit:"e0475d3", costUsd:0.12, ...}, {...} ]

GET /executions?projectId=6&status=WAITING_INPUT
200 [ {id:120, status:"WAITING_INPUT", question:"어느 워크스페이스를 쓸까요?", questionOptions:["A","B"], ...} ]

GET /executions?status=RUNNING,WAITING_CHILD&limit=100
200 [ ... ]                       # 전 프로젝트의 진행 중 실행

GET /executions?projectId=99999
404 {"statusCode":404,"error":"Not Found","message":"Project 99999 not found"}

GET /executions?status=BOGUS
400 {"statusCode":400,"error":"Bad Request","message":"알 수 없는 실행 상태입니다: BOGUS"}
```
