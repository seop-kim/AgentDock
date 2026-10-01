# 인수인계 문서 — 1차 구현 (위임 코어 + 프로젝트 상세) 2026-10-01

이 문서는 다음 작업을 이어받는 에이전트를 위한 현재 상태 스냅샷이다. 작업 방식·구조·규칙의 **기준은 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)** 이고, 이 문서는 "지금 어디까지 됐고 다음에 뭘 할지"만 다룬다. **CONVENTIONS.md 를 먼저 읽는다.**

목업 단계의 기록은 `mockup` 브랜치(→ `dev` 머지 완료)와 `apps/mockup` 에, 목업 이전 구현 단계 기록은 [`HANDOVER-implementation.md`](./HANDOVER-implementation.md) 에 있다.

## 1. 지금 상황 한눈에

| 항목 | 내용 |
| --- | --- |
| 방침 | 목업을 기준으로 **실제 기능 1차 구현**을 진행했다. 범위는 **위임(실행 트리) 코어 + 프로젝트 상세 최소 화면**. 사용자가 정했다: "백엔드 기반은 유지하고 확장, 프론트는 목업 디자인으로 재구성 / 1차는 위임 코어 먼저 + 최소 화면". |
| 작업 브랜치 | `feat/delegation` (기준 `dev`). 커밋 5개. **PR 은 아직 만들지 않았다**(`dev` 로 squash merge + `[Feat] …` 예정). |
| 검증 | JUnit **90건 통과**, `npm run build --workspace=apps/frontend` 통과, **실제 CLI 로 마스터→팀원 병렬 위임 → 취합 `done` 까지 실측**했고 화면(라이트/다크)도 브라우저로 확인했다. |
| 남은 화면 | 목업에 있는 **구성도 캔버스, 파일 첨부, 현실성 점검, 설정 화면(안쪽 메뉴·테마 화면), Tasks/실행 목록 화면**은 아직 이식하지 않았다. |

## 2. 이번에 만든 것

**백엔드(위임 코어)** — 명령 하나가 실행 트리 하나가 된다.

- `POST /projects/{id}/commands` `{text, targetAgentId?, groupId?}` → Task + 실행 트리 시작(대상 없으면 마스터, 그룹을 주면 그 리더). 채팅 기록은 **Task 로 표현**한다(사용자 말풍선 = `title`, 응답 = 루트 실행의 `result_text`).
- 판단 실행(마스터·리더)은 출력 끝에 **계약 JSON**(`delegate`/`done`)을 남기고, `delegate` 면 자식 실행을 만들어 **동시에** 돌린 뒤(`WAITING_CHILD`) 결과를 붙여 **다시 판단**한다. `done` 이면 끝난다. 자식이 리더면 같은 규칙으로 재귀한다.
- 계약 파싱 실패 → **1회 재시도** → **규칙 라우터**(팀 이름이 요청에 걸리면 그 리더) → 사람에게 넘김(`markEscalated`).
- 상한은 `application.yaml` 의 `agentdock.delegation.*`(깊이 3 · 트리 20건 · 판단 4회 · 대상 4건 · **동시 CLI 4** · 예산 5달러). 순환 위임은 거부하고 사유를 실행 로그에 남긴다.
- 프롬프트 계층(마스터 → 그룹 → 에이전트)은 `agent/PromptLayers` 가 이어 붙여 `--append-system-prompt` 로 넘긴다.
- 계측(토큰·비용·시간·세션)은 CLI 가 준 값만 저장한다. SSE 는 **구독 시 로그 재생 + `exit` 이벤트**로 바뀌어 터미널 창을 나중에 열어도 전체가 보인다.
- 새 API: `GET /executions/{id}/tree`(깊이 포함 평평한 목록), `PUT /projects/{id}/master`, `PUT /groups/{id}` 의 `prompt`, `GET /executions/{id}/logs`(기존).
- 스키마는 **V10 마이그레이션**으로 추가했다(마스터·프롬프트·실행 트리·계약·지표 컬럼, `WAITING_CHILD`).

**프론트엔드(목업 이식)** — `apps/frontend` 를 목업의 디자인 시스템으로 재구성했다.

- 이식: `src/styles/{tokens,globals,glass,shared,modal,execution,terminal}.css`(라이트/다크 3블록), `src/components/Sidebar.tsx`(+ 접이식·**테마 전환 버튼**), `src/store/ThemeContext.tsx`, `src/lib/storage.ts`, `index.html` 의 테마 초기 스크립트.
- 새 화면: **프로젝트 상세** `/projects/:id` — 왼쪽(프로젝트 카드·마스터 변경·에이전트 목록·팀 목록) + 오른쪽 **명령(채팅) 패널**(대상 선택 · 기록 · 실행 요약 카드 · 입력). **실행 트리 창**과 **터미널 새 창**(`/terminal/:executionId`, SSE 재생·라이브 + `exit` 이벤트).
- 기존 화면(Providers/Workspaces/Agents/Groups/Tasks/ExecutionDetail)은 그대로 살렸고, 새 토큰/사이드바 레이아웃을 입었다. 프로젝트 목록에서 상세로 들어가는 링크를 추가했다.
- 캔버스·첨부는 **자리만 비워 뒀다**: 왼쪽 열 + 오른쪽 패널 구조가 목업과 같아서 그대로 끼워 넣으면 된다.

**폴더 정리(같은 브랜치에서 이어서)** — 규칙은 CONVENTIONS 의 "Backend 모듈"·"프론트엔드 폴더 규칙"에 있다.

- 백엔드: 도메인 우선 + 계층 분해(`agent/{domain,controller,service,repository,dto,util}`, 인터페이스는 `interfaces/` — `interface` 는 Java 예약어라 못 쓴다). Spring Data 리포지토리 인터페이스는 `repository/` 에 함께. 158개 파일 이동, 테스트는 대상 클래스와 같은 패키지로.
- 프론트엔드: `features/<도메인>/{…}` (project·execution·agent·runtime·workspace·task·dashboard) + 공용 `components/`·`lib/`·`store/`·`styles/`. 31개 파일 이동.
- 둘 다 **동작 변경 없음**: JUnit 90건 통과, 프론트 tsc+vite 빌드 통과, 프로젝트 상세 화면(라이트) 브라우저 확인.

## 3. 실측으로 확인한 사실 (추측 금지 — 이걸 어기면 다시 깨진다)

1. **프롬프트는 표준입력(UTF-8)으로 넘긴다.** `-p` 인자로 넘기면 JSON·따옴표가 섞인 긴 문장이 CLI 에 온전히 전달되지 않는다(모델이 "요청 내용 없음"이라고 답했다).
2. **`--json-schema` 는 쓸 수 없다.** Java 의 Windows 인자 인용 때문에 따옴표가 든 JSON 이 전달되지 않고(파일 경로도 "not valid JSON" 으로 거부), 스키마를 여러 줄로 넘겨도 거부된다. → 계약은 **프롬프트 본문**에 적어 주고 결과 텍스트에서 JSON 을 찾아 파싱한다(코드블록·앞뒤 설명 허용).
3. `--output-format stream-json`(+`--verbose`)은 `--output-format json` 과 같은 계측값을 주면서 로그를 실시간으로 준다. `assistant` 이벤트의 content 블록이 로그 줄이 된다.
4. `--max-budget-usd` 로 스텝 예산을 CLI 쪽에서도 막을 수 있다.
5. 한 실행이 여러 스텝으로 이어질 때 **스텝이 끝나도 `SUCCEEDED` 로 두면 화면이 "완료"로 잘못 보인다**(재시도 중에 실제로 겪었다). 판단 중에는 `RUNNING`/`WAITING_CHILD` 로 유지한다.
6. **기본 워크스페이스를 바꿀 때 부분 유니크 인덱스를 잠깐 위반**해 500 이 났다(새 기본을 넣기 전에 기존 기본을 내려야 한다 — `ProjectService.assignWorkspace` 에서 수정).
7. **Command Code(`cmdc`) 프롬프트도 표준입력(UTF-8)으로 넘긴다.** `-p --output-format json`(v1.73.2 실측)은 **NDJSON 이벤트 스트림 + 마지막 result 한 줄**이다. result 줄은 camelCase — `sessionId`·`usage.inputTokens/outputTokens/cacheReadTokens/cacheWriteTokens`·`durationMs`·`stopReason`·`finalText` 이고 **비용 필드는 없다(→ `costUsd` 는 null, 추정 금지)**.
8. **Command Code 무인 실행에는 `-t`(프로젝트 자동 신뢰)가 필요하다.** 없으면 처음 보는 폴더에서 권한 프롬프트에 멈출 수 있다. `--permission-mode` 값은 standard·plan·accept-edits·yolo(`cmdc --help`).
9. **Command Code 모델 목록은 `cmdc --list-models` 로 자동 수집된다**(v1.73.2 기준 86개). Claude 와 달리 CLI 가 목록을 준다. 확인된 플래그 전체는 `agents/CONVENTIONS.md` 의 "Command Code(`cmdc`) 런타임" 절에 있다.

## 4. 실행과 검증 방법

```
# 백엔드 (JDK 25 필요)
$env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'
cd apps/backend; .\gradlew.bat test          # JUnit 90건
npm run dev:backend:test                     # 8081 (Flyway 자동 적용, 시작 로그 확인)
# 프론트
$env:VITE_API_BASE='http://localhost:8081'; npm run dev:frontend:test   # 3031
npm run build --workspace=apps/frontend      # tsc --noEmit && vite build (커밋 전 필수)
```

- 사용자 포트 8080/3030 은 쓰지 않는다. 에이전트는 **8081/3031** 만 쓰고 끝나면 즉시 종료한다.
- **실제 CLI 로 확인하는 순서**(이번에 쓴 방법): 프로젝트에 마스터를 지정하고(`PUT /projects/{id}/master`) 기본 워크스페이스에서 런타임을 확인한 뒤(`POST /workspaces/{id}/runtimes/{providerId}/check` → `CONNECTED`) `POST /projects/{id}/commands` 로 명령을 보내고 `GET /executions/{id}/tree` 를 폴링한다. 위임이 일어나면 트리에 depth 1 자식들이 생기고, 끝나면 루트가 `SUCCEEDED/DONE` + `summary` 로 바뀐다.
- **PowerShell 로 한글 본문을 보낼 때 주의**: `Invoke-RestMethod -Body ($obj | ConvertTo-Json)` 은 본문을 기본 인코딩으로 보내 **한글이 `?` 로 깨진다**(이번에 이것 때문에 모델이 "mojibake"라고 답했다). UTF-8 바이트로 보낸다: `[System.Text.Encoding]::UTF8.GetBytes($json)` + `-ContentType 'application/json; charset=utf-8'`.
- 화면 검증은 `agent-browser`(open/snapshot -i/click/fill/screenshot). **화면을 다시 열면 ref 가 전부 바뀐다** → 필요할 때마다 스냅샷을 다시 뜬다. ref 는 `'@e30'` 처럼 따옴표로 감싼다.
- 검증 중에 백엔드를 재시작하면 돌던 실행이 `RUNNING`/`WAITING_CHILD` 로 남는다(제품에 정리 로직이 아직 없다 — 아래 5장).

## 5. 다음에 할 일 (순서는 사용자에게 묻는다)

1. **구성도 캔버스 이식**(목업 `GroupCanvas`): 배치·이동·줌·맞춤, 왼쪽 에이전트/그룹 카드, 그룹 만들기·프롬프트 편집. 지금 프로젝트 상세의 왼쪽 열을 캔버스로 바꾸면 된다.
2. **파일 첨부**: `POST /workspaces/{id}/...` 로 파일을 `.agentdock/attachments` 에 복사하고(uuid 앞 8자 이름), 실행 프롬프트에 그 경로를 붙인다. 백엔드에 첨부 테이블이 아직 없다(V11 필요).
3. **Tasks/실행 목록 화면**: 전 프로젝트의 진행/종료 작업과 실행 로그. 지금은 프로젝트 상세의 채팅 기록으로만 볼 수 있다.
4. **화면에서 마스터·그룹 프롬프트 편집**: 백엔드 API 는 있다(`PUT /projects/{id}/master`, `PUT /groups/{id}`). 프로젝트 설정 창(목업)을 옮기면 된다.
5. **중단된 실행 정리**: 서버가 죽으면 `PENDING`/`RUNNING`/`WAITING_CHILD` 로 남아 화면이 "실행 중"으로 계속 보이고 폴링도 계속 돈다. 기동 시 정리하거나 화면에서 취소할 수 있게 해야 한다(취소 API 는 있다: `POST /executions/{id}/cancel`).
6. **취소 전파**: 부모를 취소하면 자식도 취소해야 한다(지금은 각각만 취소된다).
7. **세션 재개(`--resume`)**: 마스터 대화 연속성. 지금은 스텝마다 새 세션이다(세션 id 는 저장한다).
8. **`docs/planning/` 본문 작성**: 화면·흐름이 확정된 것부터. 사용자가 내용을 주는 대로 쓴다.

## 6. 알려진 함정 / 메모

- **개발 DB(`AGENT_DOCK`)에 이번 검증용 데이터가 남아 있다**: 프로젝트 `test`(id 4), 에이전트 `총괄 마스터`(8)·`백엔드 A`(9)·`백엔드 B`(10)·`test`(7), 그룹 `백엔드 팀`(1, 리더 7), 워크스페이스 `delegation-test`(6, 임시 폴더) + `agentDock`(5), 실행 기록. 필요하면 지운다.
- `execution.status` 의 `WAITING_CHILD` 는 DB 제약이 없어(컬럼이 `VARCHAR(32)`) 마이그레이션에서 제약을 건드리지 않았다.
- JPA 는 `validate` 라 **엔티티와 스키마가 어긋나면 기동이 실패**한다 — 마이그레이션을 추가하면 엔티티도 같이 고치고, 기동 로그(`Started AgentDockApplication`)를 확인한다.
- 위임 실행 중 만든 자식은 **부모와 같은 실행 폴더(프로젝트 기본 워크스페이스)** 에서 돈다. worktree 격리는 아직 없다.
- 목업 앱(`apps/mockup`, 포트 3040)은 **기준 화면**이다. 이식할 때 값을 비교하되 목업 자체는 고치지 않는다.
