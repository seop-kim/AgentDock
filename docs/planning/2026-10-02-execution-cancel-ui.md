# 실행 취소 UI 화면 흐름 정의 (2026-10-02)

`agents/HANDOVER.md` 5장 5번(중단된 실행 정리) 중 **화면에서 수동 취소**하는 경우의 화면 흐름 정의다. 버튼 위치, 확인 절차, 취소 후 상태 표시·갱신 방식을 정한다.

**요구사항 담당(agent id=24)의 범위 정의서 [`2026-10-02-stale-execution-cleanup-scope.md`](./2026-10-02-stale-execution-cleanup-scope.md) 와 정합한다.** 그 문서의 추천안 — A3(자동 정리 + 수동 취소 둘 다) · B1(전이 상태 `CANCELLED`) · C2(대상 4종) · D2(수동 취소는 **행 단위만**) · E(cancel API 상태 전이 버그 수정 포함, 프론트는 트리 창과 실행 요약 카드 양쪽에 버튼) — 을 전제로 쓴다. A(적용 방식)와 E(버그 수정 포함 여부)는 그 문서 §6 에서 **사용자 최종 확인 대기** 중이라, 확정 전에는 아래 전제가 바뀔 수 있다.

## 0. 전제와 근거

- **수동 취소를 포함한다**(id=24 A3 추천). 자동 정리만 하면 취소 버튼은 없고 §5-10(표시 방식)만 해당한다.
- **취소 대상 상태 4종**: `PENDING` / `RUNNING` / `WAITING_CHILD` / `WAITING_INPUT`(id=24 C2/E). 백엔드 `ExecutionService.isRunning`(L201)과 프론트 `lib/executions.ts` `isLive()` 가 이미 같은 4종을 "아직 안 끝남"으로 묶는다(프론트는 `PENDING` 을 `QUEUED` 로 부른다). 종료(`SUCCEEDED`/`FAILED`/`CANCELLED`)에는 버튼을 두지 않는다.
- **취소 후 상태는 `CANCELLED`**(id=24 B1). `errorMessage` 에 사유를 남긴다.
- **취소 전파는 범위 밖**(id=24 §5, HANDOVER 5-6). 그래서 수동 취소는 **각 실행 행 단위**로만 하고, 부모를 취소해도 자식은 멈추지 않는다(id=24 D2/E). 트리 전체를 한 번에 멈추는 버튼은 두지 않는다.
- **cancel API 를 이번 범위에서 고친다**(id=24 E): 지금은 OS 프로세스만 죽이고 DB 상태를 바꾸지 않는다. 이번에는 "프로세스 종료 + `execution.status = CANCELLED` 전이 + 루트면 Task 상태 동기화"까지 해야 버튼이 실제로 동작한다.
- 근거 코드(실측): `execution/controller/ExecutionController`(`POST /executions/{id}/cancel`) · `execution/service/ExecutionService`(`cancel` L325, `isRunning` L201) · `process/service/ProcessService.cancel` · `delegation/service/DelegationService`(판단 루프) · `apps/frontend/src/lib/api.ts`(`cancelExecution`) · `pages/Projects/{ExecutionTree,ChatPanel,ProjectDetail}.tsx` · `store/AgentDockStore.tsx` · `lib/executions.ts`.

## 1. 현재 상태 (사실 — 이미 있는 것과 새로 만들 것)

| 항목 | 상태 |
| --- | --- |
| 취소 API | 있다. `POST /executions/{id}/cancel` → 201 `{cancelled:true}`. **하는 일은 그 실행의 CLI 프로세스 `destroy()` 하나뿐**이다(`ProcessService.cancel`). DB 상태를 바꾸지 않는다(이번 범위에서 수정). |
| 프론트 API 클라이언트 | 있다. `api.cancelExecution(id)`(`lib/api.ts`). **호출하는 화면이 없다.** |
| 상태 이름 | 백엔드는 `PENDING/RUNNING/WAITING_CHILD/WAITING_INPUT/SUCCEEDED/FAILED/CANCELLED`. 프론트 스토어(`AgentDockStore.executionStatus`)가 `PENDING→QUEUED`, `SUCCEEDED→DONE`, **`CANCELLED→FAILED`** 로 접는다. 그대로 두면 취소가 화면에 **"실패"** 로 보인다(§5-7). |
| `CANCELLED` 전이 | **어디서도 설정되지 않는다**(enum 과 로그 라벨에만 존재). 취소된 실행은 러너가 돌아와 `applyResult` 에서 `FAILED` 로 기록된다(이번 범위에서 수정). |
| 취소 전파 | 없다. 자식을 취소해도 부모는 다음 판단 스텝으로 계속 돈다(`runChild` 가 `FAILED` outcome 을 받는다). 부모(루트)를 취소해도 이미 돈 자식은 계속 돈다(HANDOVER 5-6, 이번 범위 밖). |
| 실행 트리 창 갱신 | 열려 있는 동안 스토어 `executions` 가 `useLiveRefresh`(3s)로 갱신되므로 배지는 살아 있다. |
| 폴링 종료 조건 | 스토어 `watch()` 는 루트가 `SUCCEEDED/FAILED` 일 때만 멈춘다. 루트가 `CANCELLED` 면 **폴링이 끝나지 않는다**(반영 필요, §5-7). |
| 취소된/실패한 트리 결과 | `start()` 끝에서 항상 `collectTreeResult` 를 타므로 **부분 변경이 커밋·병합**된다. 취소 후에도 루트 행·요약 카드에 병합 결과 줄이 붙을 수 있다. |
## 2. 취소 버튼 노출 조건

- **4종**(`PENDING`/`RUNNING`/`WAITING_CHILD`/`WAITING_INPUT`)에만 노출한다(id=24 C2/E). 판정은 프론트 `lib/executions.ts` 의 `isLive(status)` 하나로 끝난다 — 이미 이 4종을 true 로 본다(백엔드 `ExecutionService.isRunning` 과 같은 기준).
- `SUCCEEDED`/`FAILED`/`CANCELLED`(종료)에는 노출하지 않는다.
- `WAITING_INPUT`(사람의 답을 기다리며 멈춘 상태)도 포함한다 — 답을 주지 않고 실행을 포기하는 길이기 때문이다(id=24 C2).

## 3. 버튼 노출 위치 (결정: 실행 행 단위만)

id=24 D2/E 에 따라 **취소 전파를 하지 않으므로 트리 전체(루트/헤더) 취소 버튼은 두지 않는다**(누르면 자식이 계속 도는데 "전체 취소"는 거짓이 된다). 실행 하나당 버튼 하나를 둔다.

- **실행 트리 창** `ExecutionTree.tsx` 각 행: `rowHead` 안 `rowMetrics` 뒤에 `exec.cardButton` 스타일의 `취소`('워크트리 정리'·'다시 병합'과 같은 자리·스타일). 그 행의 상태가 `isLive` 일 때만. 누르면 **그 실행 하나만** 취소한다.
- **채팅 실행 요약 카드** `ExecutionSummaryCard`: 각 스텝 줄(`exec.steps` 의 `li`)에 같은 `취소` 버튼 — id=24 완료 조건이 **트리 창과 실행 요약 카드 양쪽** 노출을 명시한다. 머리말(cardHead)에는 두지 않는다(머리말은 트리 전체를 뜻하므로 오해를 준다).
- **터미널 새 창** `TerminalWindow` 머리말: 그 에이전트의 현재 실행이 `isLive` 면 `취소`(선택). 창을 띄워 둔 채 멈추는 흐름.
- **에이전트 카드 배지**(`lib/agentStatus.ts`)는 실행이 아니라 Task 기준이므로 버튼을 두지 않는다.

## 4. 확인 절차

- **확인 다이얼로그를 둔다**(되돌릴 수 없음). 이 저장소의 파괴적 확인 선례가 `window.confirm` 이다(워크트리 정리·그룹/에이전트 삭제·런타임 삭제) → 같은 방식을 따른다.
- 문구(전파가 없으므로 "이 실행만"을 분명히 한다): `"<이름>"의 실행을 취소할까요?\n지금 도는 이 실행만 멈춥니다. 하위·다른 실행은 계속 돕니다.`
- 취소돼도 **부분 변경의 커밋·병합이 일어날 수 있음**을 문구에 덧붙인다(§1).
- 옵션: 스타일된 확인 창이 필요하면 `styles/modal.module.css` + `createPortal`(다른 창들과 같은 방식)로 만들어도 된다. 단순 예/아니오는 `window.confirm` 선례를 따른다.

## 5. 취소 후 상태 표시 및 화면 갱신

1. 클릭 → 확인 → 그 행 버튼을 `취소 중…` 으로 바꾸고 disabled(중복 클릭 방지).
2. `api.cancelExecution(id)` 호출. 이번 범위에서 서버가 **프로세스 종료 + `execution.status = CANCELLED` 전이 + (루트면) Task 상태 동기화**까지 한다(id=24 E).
3. 성공(201) 후 `reload()` → 그 행이 배지 **`취소`**(중립 톤)로 바뀌고 버튼이 사라진다(id=24: 화면이 즉시 `CANCELLED` 로 갱신).
4. 취소 사유(`errorMessage`, 예: 서버 재기동 자동 정리)는 그 행에서 툴팁/로그로 볼 수 있게 한다.
5. `cancelled:false`(프로세스를 못 찾음 — 이미 끝난 뒤 클릭) 또는 404 → 오류로 보지 않고 `reload()` 로 실제 상태를 보여 준다.
6. 네트워크/5xx → 오류 안내(ProjectDetail 의 `notice`/`fail`), `취소 중…` 해제.
7. **프론트 상태 매핑 반영(전제)**: (a) `executionStatus()` 에서 `CANCELLED` 를 `FAILED` 로 접지 않고 그대로 통과시킨다, (b) `types.ts` `ExecutionStatus` 에 `'CANCELLED'` 를 추가한다, (c) `lib/executions.ts` 의 `STATUS_LABEL`="취소" · `STATUS_TONE`(기존 `wait` 재사용 또는 전용 muted 톤) · `isLive` 는 4종 그대로 둔다(종료라 자동 제외), (d) 스토어 `watch()` 의 종료 판정에 `CANCELLED` 를 추가한다. (d)가 없으면 **취소해도 폴링이 멈추지 않고**, (a)가 없으면 **"실패"로 보인다**.
8. **전파가 없으므로**: 취소한 행만 종료가 되고 형제·자손은 계속 `isLive` 로 남는다. 화면은 "트리 전체가 멈췄다"고 말하지 않고 **행별 상태만** 정확히 보여 준다.
9. **트리 결과**: 취소로 끝난 트리도 루트에 `collectTreeResult` 가 돌아 병합 결과 줄(병합됨/수동 병합 필요)이 붙을 수 있다. 취소 표시와 별개로 그대로 보여 준다.
10. **자동 정리(id=24 A1/A3)와의 관계**: 서버 재기동 시 고아 4종이 `CANCELLED` + 사유(`errorMessage`, "서버 재기동으로 중단됨")로 **기동 1회** 일괄 전이된다. 화면은 별도 조작 없이 새로고침만으로 그 실행들을 `취소` 로 보여 준다(버튼 없음). 폴링도 `CANCELLED` 를 종료로 인정해야 멈춘다(§5-7).
11. **Task 상태 동기화**: 루트 실행을 취소하면 Task 도 종료로 갱신되어(id=24 E) 채팅의 "작업 중…" 표시와 에이전트 카드의 "작업 중/작업 대기중"이 풀린다. 자식 실행 취소는 Task 에 영향이 없다(트리 루트가 아님).

## 6. 완료 조건 (화면)

- `isLive`(4종) 실행에만 취소 버튼이 보이고(실행 트리 창 + 실행 요약 카드), 종료 실행에는 없다.
- 버튼 클릭 시 확인 절차를 거치고, 진행 중에는 `취소 중…` 으로 중복 클릭을 막는다.
- 취소된 실행이 화면에서 **`취소`** 로 표시되고(**실패와 구분**), 사유가 보이며, 폴링이 멈춘다.
- 루트를 취소하면 Task 도 종료로 바뀌어 "작업 중/작업 대기중" 표시가 풀린다.
- 취소 대상이 없거나 이미 끝난 뒤 눌렀으면 **오류 없이** 실제 상태로 수렴한다.
- 전파가 없으므로, 한 행을 취소해도 형제·자손 행은 계속 live 로 남는다(트리 전체 취소 버튼은 없다).

## 7. 요구사항 담당 결정과의 정합 / 남은 확인

- 이 문서가 따르는 결정(요구사항 담당 agent id=24, 추천안): **A3**(자동 정리+수동 취소) · **B1**(`CANCELLED`) · **C2**(대상 4종) · **D2**(수동은 행 단위, 트리 전체 아님) · **E**(cancel API 상태 전이 버그 수정 포함). 출처: [`2026-10-02-stale-execution-cleanup-scope.md`](./2026-10-02-stale-execution-cleanup-scope.md).
- **사용자 최종 확인 필요**(id=24 §6): A(둘 다 vs 하나), E(버그 수정 포함 여부). 이 문서는 **추천안 기준**으로 썼다.
  - **A2(수동 취소만)** 이면: 취소 버튼 흐름은 그대로 유효하고 §5-10(자동 정리 표시)만 빠진다.
  - **A1(자동 정리만)** 이면: 취소 버튼(§2~§4, §5-1~§5-9, §6)이 전부 빠지고 §5-10 표시 방식만 남는다.
  - **E 를 별도 항목으로 분리**하면: 버튼만 먼저 넣게 되어 눌러도 상태가 안 바뀌므로 넣지 않는다(§0, id=24 E 추천).
- 범위 밖(요구사항 담당과 동일): 취소 **전파**(HANDOVER 5-6), 워크트리/브랜치 자동 삭제, 상시 감시(기동 1회만).