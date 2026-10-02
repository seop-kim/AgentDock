# 인수인계 문서 — 1차 구현 (위임 코어 + 프로젝트 상세) 2026-10-01

이 문서는 다음 작업을 이어받는 에이전트를 위한 현재 상태 스냅샷이다. 작업 방식·구조·규칙의 **기준은 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)** 이고, 이 문서는 "지금 어디까지 됐고 다음에 뭘 할지"만 다룬다. **CONVENTIONS.md 를 먼저 읽는다.**

목업 단계의 기록은 `mockup` 브랜치(→ `dev` 머지 완료)와 `apps/mockup` 에, 목업 이전 구현 단계 기록은 [`HANDOVER-implementation.md`](./HANDOVER-implementation.md) 에 있다.

## 1. 지금 상황 한눈에

| 항목 | 내용 |
| --- | --- |
| 방침 | 목업을 기준으로 **실제 기능 1차 구현**을 진행했다. 범위는 **위임(실행 트리) 코어 + 프로젝트 상세 최소 화면**. 사용자가 정했다: "백엔드 기반은 유지하고 확장, 프론트는 목업 디자인으로 재구성 / 1차는 위임 코어 먼저 + 최소 화면". |
| 작업 브랜치 | `feat/self-host-setup` (기준 `dev`, PR #7 은 `dev` 에 머지됨). 이번 작업 커밋: `c714a48`(워크트리 격리) → `0019c83`(트리 결과 커밋·자동 병합) → 문서 커밋(이 파일). **PR 은 아직 만들지 않았다**(`dev` 로 squash merge + `[Feat] …` 예정). |
| 검증 | JUnit **147건 통과**, `npm run build --workspace=apps/frontend` 통과, **실제 CLI 로 워크트리 생성 → 트리 브랜치 커밋 → 메인 저장소 자동 병합(`MERGED`)까지 실측**하고 화면(DOM)으로도 확인했다(8장). |
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
cd apps/backend; .\gradlew.bat test          # JUnit 147건
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

- **개발 DB(`AGENT_DOCK`)의 이전 검증용 데이터 일부를 정리했다**(2026-10-01, 이번 작업): AgentDock 프로젝트(id 6)의 Task/Execution 중 **에이전트 테스트가 만든 행**(task 16~25, execution 26~39)을 지웠고, 그 프로젝트에는 이제 Task/Execution 이 없다. 프로젝트 `test`(id 4)·`TestAgecnt`(id 5)의 데이터, 워크스페이스 `agentDock`(5)·`delegation-test`(6)·`AgentDock-clone`(9), 에이전트/그룹 정의는 **사용자 데이터로 보고 남겨 두었다**.
- 저장소 밖 임시 폴더: 워크트리 폴더 `C:\Users\chey.kim\Documents\GitHub\AgentDock-wt`(비었음)는 지웠다. **`C:\Users\chey.kim\AgentDock-clone` / `AgentDock-clone-wt` 는 이전 실측용으로 남아 있다**(이번 범위 밖 — 지워도 되는지 사용자 확인 필요).
- `execution.status` 의 `WAITING_CHILD` 는 DB 제약이 없어(컬럼이 `VARCHAR(32)`) 마이그레이션에서 제약을 건드리지 않았다.
- JPA 는 `validate` 라 **엔티티와 스키마가 어긋나면 기동이 실패**한다 — 마이그레이션을 추가하면 엔티티도 같이 고치고, 기동 로그(`Started AgentDockApplication`)를 확인한다.
- 목업 앱(`apps/mockup`, 포트 3040)은 **기준 화면**이다. 이식할 때 값을 비교하되 목업 자체는 고치지 않는다.

## 7. 워크트리 격리 — 실행 디렉터리 분리 (2026-10-01 추가)

지금까지 실행 트리의 모든 실행이 **같은 폴더(프로젝트 기본 워크스페이스)** 에서 돌아, 두 명령(또는 부모·자식)이 같은 파일을 동시에 고치면 서로 덮어썼다. 이제 **명령 하나(루트 실행)가 git worktree 하나**를 갖는다. 루트와 그 자식은 **같은** worktree 에서 돈다(자식의 편집을 부모가 봐야 하므로). 서로 다른 명령(다른 루트)만 서로 격리된다.

- **새 코드**: `execution/service/WorktreeService`(`isGitRepository`/`create`/`remove`, git 은 `ProcessService` 로 셸 없이 실행), `ExecutionGuard.Target.cwd()`, `ExecutionFactory`(자식이 부모 worktree 를 물려받음), `ExecutionService.recordWorktree`/`removeWorktree`, `DELETE /executions/{id}/worktree`. 마이그레이션 **V13**(`execution.worktree_path`/`worktree_branch`, 멱등).
- **흐름**: `DelegationService.start` 가 루트 실행 행을 만든 **직후** 워크스페이스가 git 저장소면 `git worktree add -b agentdock/exec-<루트id> <워크스페이스 형제폴더>-wt\<루트id>` 로 만들고, 그 트리 전체가 **cwd = worktree 경로** 로 돈다(`--add-dir` 불필요). 만들지 못하면(저장소가 아니거나 git 실패) 워크스페이스에서 그대로 돌리고 SYSTEM 로그에 사유를 남긴다 — **격리 실패가 실행을 막지 않는다**.
- **자동 병합은 8장에서 추가됐다**: 격리만 있던 시점에는 결과를 되돌리지 않았다. 지금은 트리가 끝나면 커밋하고, 메인 저장소가 깨끗하면 자동 병합한다(8장). **워크트리·브랜치 자동 삭제는 여전히 없다** — 정리는 `DELETE /executions/{id}/worktree`(`?branch=true` 면 브랜치도 삭제, 204). 아직 도는 실행(`PENDING`/`RUNNING`/`WAITING_CHILD`)이면 **409**. 지운 뒤에는 실행의 경로·브랜치를 비운다.
- **로그**: 성공 `⎿ 워크트리: <경로> (브랜치 <브랜치>)`, 실패 `⎿ 워크트리를 만들지 못해 워크스페이스에서 실행합니다: <사유>`(모두 SYSTEM).
- **화면**(`apps/frontend`, 기존 토큰·CSS 모듈 클래스만 사용 — 목업 대비 디자인 변경 없음): 터미널 창 머리말의 `워크트리 <브랜치>` 배지와 worktree 경로(`.cwd`), 실행 트리 창 각 행의 `워크트리 <경로> · <브랜치>` 줄과 루트 행의 **워크트리 정리** 버튼(`window.confirm` 후 브랜치까지 삭제).
- **프롬프트 규칙**(DB 데이터, 코드 아님): AgentDock 프로젝트(id 6)의 `project.master_prompt` 와 `agent_group.prompt` 5개 끝에 "한 트리 안에서 같은 파일을 동시에 고치지 말고 나눠 맡긴다. 워크트리 병합은 사람이 판단한다." 를 덧붙였다(멱등 UPDATE, 기존 문장 유지).

**검증(실측)**

- `.\gradlew.bat test` **143건 통과**(그 시점 기준 — 8장의 결과 수집 4건을 더해 최종 **147건**), `npm run build --workspace=apps/frontend` 통과.
- 8081/3031 로 실제 확인: 프로젝트 6(마스터 13)에 명령을 보내자 `git worktree list` 에 `C:/Users/chey.kim/Documents/GitHub/AgentDock-wt/26 [agentdock/exec-26]` 가 생겼고, 실행 로그에 `⎿ 워크트리: ... (브랜치 agentdock/exec-26)` 과 CLI init 의 `⎿ 실행 시작 (model=claude-sonnet-5, cwd=C:\...\AgentDock-wt\26)` 이 남았다 — **실행이 worktree 안에서 돌았다는 직접 증거**다. 도는 중 `DELETE .../worktree?branch=true` 는 **409**, 끝난 뒤 같은 호출은 **204**(폴더와 브랜치 삭제, `git worktree list` 에서 사라짐), 두 번째 호출은 404.
- 남은 산출물: 그때 남겨 둔 워크트리 `AgentDock-wt\27` + 브랜치 `agentdock/exec-27`(그리고 실측용 `AgentDock-wt\32` + `agentdock/exec-32`)를 **이번 작업에서 정리했다**(`git worktree remove --force` → `git branch -D` → `git worktree prune`, 빈 폴더 삭제).

## 8. 트리 결과 수집·병합 — "명령 하나의 결과를 한 번에 되돌린다" (2026-10-01 추가)

워크트리 격리(7장)로 명령 하나가 자기 작업 디렉터리에서 돌지만, **마지막 단계가 빠져 있었다** — 트리가 끝나도 그 결과가 메인 저장소로 돌아오지 않아 사용자가 합쳐진 결과를 볼 수 없었다. 이제 트리(루트 실행)가 끝나면 **커밋 하나를 남기고 메인 저장소가 깨끗하면 자동으로 병합**한다.

- **새 코드**: `WorktreeService.collect`(트리 브랜치에 커밋 + 메인 저장소 병합, 실제 git — 셸 미경유), `execution/domain/{MergeStatus,ChangedFile}`, `ExecutionService.recordTreeResult`, `DelegationService.collectTreeResult`/`commitMessage`/`participants`/`logTreeResult`. 마이그레이션 **V14**(`execution.result_commit`/`merge_status`/`merge_detail`/`changed_files`, 멱등). `Execution`·`ExecutionResponse` 에 필드 추가.
- **동작**: `DelegationService.start` 의 루트 백그라운드 작업이 `runExecution`(트리 실행) → `collectTreeResult` → `closeStream` → `publishFinished` 순서로 돈다(로그가 라이브로도 보이게 스트림을 닫기 전에 되돌린다).
  - **커밋**: 변경이 있으면 트리 브랜치(`agentdock/exec-<루트id>`)에 커밋 하나. 제목 = 루트 실행의 최종 요약(계약 `done.summary`/`result_text`), 없으면 `실행 #<id> 작업 결과`. 본문 = 실행 id·참여 에이전트 이름·변경 파일 수. **`Co-Authored-By` 없음**. 커밋할 것이 없으면 빈 커밋 없이 `NONE`.
  - **병합**: 메인 작업 트리가 깨끗하면 `git merge --no-ff <트리브랜치>` → `MERGED`. 더럽거나 충돌하면 `git merge --abort` 로 되돌리고 사유와 함께 `MANUAL`(직접 병합할 브랜치를 로그로 알린다). 병합은 공정 세마포어로 **전역 직렬화**(여러 트리가 동시에 끝날 수 있다).
  - **기록/로그(루트에만)**: `result_commit`/`merge_status`/`merge_detail`/`changed_files` 를 루트 실행에 남기고 응답에 싣는다. SYSTEM: `⎿ 변경 N개를 커밋했습니다 (<sha>)` → `⎿ 메인 저장소(<브랜치>)로 병합했습니다` 또는 `⎿ 자동 병합하지 못했습니다(사유). 브랜치 <브랜치> 를 직접 병합하세요`.
- **프롬프트 규칙**(DB 데이터, 코드 아님): AgentDock 프로젝트(id 6)의 `project.master_prompt` 와 `agent_group.prompt` 5개 끝에 "작업이 끝나면 변경 사항을 트리 브랜치에 커밋하고, 메인 저장소가 깨끗하면 자동으로 병합한다. 병합하지 못하면 사유와 브랜치를 사용자에게 알린다." 를 덧붙였다(멱등 UPDATE, 기존 문장 유지).

**남은 한계 (설계대로)**

- **워크트리·브랜치 자동 삭제 없음**: 병합에 성공해도 워크트리와 트리 브랜치는 남는다. 정리는 여전히 사람이 `DELETE /executions/{id}/worktree`(`?branch=true`)로 한다.
- **더러운 메인 저장소면 자동 병합하지 않는다**: 커밋되지 않은 변경이 있으면(`git status` 가 비어 있지 않으면) `MANUAL` 로 남기고 사유·브랜치만 알린다. 사용자가 그 변경을 정리한 뒤 브랜치를 직접 병합해야 한다. **저장소에 추적되지 않는 파일/폴더가 하나만 있어도(`?? …`) MANUAL 이 된다** — 실측 중 실제로 겪었다(작업 폴더의 도구 폴더 때문에 `MERGED` 가 안 나왔다).
- **되돌리기는 루트 실행에만 기록**하고 자식에는 남기지 않는다(트리 결과는 루트의 것).
- **CLI 권한 모드가 쓰기를 막으면 트리가 `NONE` 으로 끝난다**: 에이전트 `mode`(=`--permission-mode`)가 기본값이면 무인 실행에서 CLI 가 파일 쓰기를 거부한다(`Claude requested permissions to write …` 로그). 이번 실측에서 마스터(13)와 백엔드 리더(14)가 이 때문에 파일을 못 만들어 두 번 `NONE` 이 나왔고, 쓰기 허용 모드(`acceptEdits`)를 준 마스터로 다시 돌려 `MERGED` 를 확인했다. 파일을 고쳐야 하는 에이전트에는 모드를 지정해 둔다(DB 데이터).

**검증(실측)**

- `.\gradlew.bat test` **147건 통과**(기존 143 + 결과 수집 4: `WorktreeResultCollectTest` — 깨끗한 메인 → MERGED, 더러운 메인 → MANUAL(반쯤 병합 없음), 변경 없음 → NONE, 변경 파일 파싱), `npm run build --workspace=apps/frontend` 통과.
- 8081/3031 로 실제 확인(2026-10-01): 프로젝트 6(마스터 13)에 명령을 보내 **워크트리 생성 → 트리 브랜치 커밋 → 메인 저장소 자동 병합**을 끝까지 확인했다.
  - `GET /executions/39` → `worktreePath=C:\Users\chey.kim\Documents\GitHub\AgentDock-wt\39`, `worktreeBranch=agentdock/exec-39`, `resultCommit=e0475d3`, `mergeStatus=MERGED`, `changedFiles=[{status:A, path:AGENTDOCK_LIVE_CHECK.md}]`.
  - SYSTEM 로그: `⎿ 워크트리: …\AgentDock-wt\39 (브랜치 agentdock/exec-39)` → `⎿ 실행 시작 (model=claude-sonnet-5, cwd=…\AgentDock-wt\39)` → `⎿ 변경 1개를 커밋했습니다 (e0475d3)` → `⎿ 메인 저장소(feat/self-host-setup)로 병합했습니다`.
  - git: 트리 브랜치 커밋 `e0475d3` 은 제목이 루트 실행 요약, 본문이 `실행 #39 / 참여: 총괄 / 변경 파일 1개`(Co-Authored-By 없음), 메인 저장소에는 `49fba63 Merge branch 'agentdock/exec-39'` 병합 커밋이 생겼다.
  - 화면(3031, DOM): 채팅 실행 요약 카드 `병합됨 · 커밋 e0475d3 · A AGENTDOCK_LIVE_CHECK.md`, 실행 트리 창 루트 행 `병합됨 · 커밋 e0475d3 · A AGENTDOCK_LIVE_CHECK.md` + 워크트리 줄 + 정리 버튼, 터미널 머리말 `워크트리 agentdock/exec-39`·`커밋 e0475d3`·작업 디렉터리 `…\AgentDock-wt\39`.
  - 확인 후 되돌림: 검증이 만든 커밋은 `git reset --hard 0019c83` 으로 지우고(작업 트리에 남은 `AGENTDOCK_LIVE_CHECK.md` 도 함께 사라짐), 워크트리 33·34·39 와 브랜치 `agentdock/exec-33/34/39` 를 삭제했고, 검증용 DB 행(task 23~25, execution 33~39, 로그 74줄)도 지웠다.

## 9. 이번 작업이 남긴 저장소 상태 (2026-10-01)

- **커밋**: `c714a48 feat(worktree): 실행을 git worktree 로 격리하고 결과를 남긴다` → `0019c83 feat(execution): 트리 결과를 커밋하고 자동 병합해 화면에 보여준다` → 문서 커밋(이 문서 + `CONVENTIONS.md`). 브랜치 `feat/self-host-setup`, `dev` 는 아직 이 커밋들을 받지 않았다(PR 예정).
- **중간 상태를 실제로 갈랐다**: 이번 작업 전에는 워크트리 격리와 결과 수집이 **한 작업 트리에 섞여** 있었다. 커밋 1 은 격리만(테스트 143건), 커밋 2 는 결과 수집·병합(V14 + `WorktreeResultCollectTest`, 테스트 147건)으로 나눠 각 커밋에서 `gradlew test` 와 프론트 빌드를 통과시켰다.
- **정리한 것**: 워크트리 `AgentDock-wt\27`(브랜치 `agentdock/exec-27`), `AgentDock-wt\32`(`agentdock/exec-32`, 커밋 `db868b6`), 이번 실측용 `33/34/39` + 그 브랜치들, 빈 `AgentDock-wt` 폴더. 프로젝트 6 의 테스트 Task/Execution 행.
- **남겨 둔 것(사용자 판단 필요)**: 저장소 루트의 추적되지 않는 `.commandcode/`(에이전트 도구 폴더 — 이 때문에 자동 병합 판정이 `MANUAL` 이 될 수 있다. `.gitignore` 에 넣을지 결정 필요), `C:\Users\chey.kim\AgentDock-clone` / `AgentDock-clone-wt`(이전 실측용 클론).
- **실행 중 남은 프로세스 없음**: 검증용 8081/3031 서버는 종료했고, 사용자의 8080 서버는 건드리지 않았다.
- **다음 후보**: (1) 워크트리·브랜치 자동 정리 옵션(병합 성공 후), (2) `MANUAL` 인 트리를 화면에서 바로 병합하도록 돕는 버튼, (3) 저장소에 추적되지 않는 파일이 있어도 병합할지 정하는 정책(현재는 무조건 `MANUAL`).

## 10. 위임 프롬프트를 파일로 넘긴다 (2026-10-02 추가)

마스터·리더가 위임할 때 **자식의 전체 지시(원래 요청·맡은 일·기대 결과·지난 결과·첨부 경로)를 프롬프트에 인라인**하던 것을 **작업 디렉터리(cwd) 안 `.agentdock/prompts/<자식 id>.md` 파일로 옮기고 프롬프트에는 짧은 참조만** 남긴다. 프롬프트가 길어질수록 CLI 입력이 커져 토큰이 낭비되고 기록이 읽기 어려워지기 때문이다. 규칙은 `agents/CONVENTIONS.md` 의 "위임 프롬프트는 파일로 넘긴다" 절에 있다.

- **새 코드**: `delegation/util/DelegationPrompts`(`childInstruction`/`instructionReference`/`inlineInstruction`/`stepContextReference`/`childPromptFileName`/`stepPromptFileName`/`promptFileReference` — 순수 문자열), `delegation/service/DelegationService`(`writePromptFileLogged` — cwd 기준 `.agentdock/prompts` 에 UTF-8 로 쓰고 SYSTEM 로그, `stepContext`, `attachmentsIn`, `executionInstruction`). `AttachmentService.ATTACHMENT_PROMPT_HEADER` 상수 추가(위임이 `[첨부 파일]` 절을 떼어 내므로 머리말을 한 곳에서 정의). 스키마 변경 없음.
- **동작**: 자식 행을 만든 뒤(**id 가 필요해**) 지시 파일을 쓰고, `child.prompt` 를 `지시 파일: .agentdock/prompts/<id>.md — 이 파일을 먼저 읽고 그 내용대로 작업하세요. 기대 결과: <expects>` 로 저장한다. 위임받은 **판단 자식**도 같은 참조를 지시로 받고(팀 로스터·판단 규칙·계약 스키마는 짧아 인라인 유지), **루트**는 원래 요청을 그대로 쓴다. 부모의 재판단 문맥(자식 결과·사람의 답)은 `<실행id>-step<N>.md` 로 넘긴다.
- **폴백**: 파일을 못 쓰면 사유를 SYSTEM 로그로 남기고 예전처럼 지시·문맥을 프롬프트에 인라인한다(실행을 막지 않는다).
- **로그(SYSTEM)**: 파일마다 `⎿ 지시 파일: <경로> (N자)`, 스텝마다 `⎿ 프롬프트 <N>자`(CLI 에 실제로 넘긴 크기). 새 로깅 프레임워크는 넣지 않았다.
- **`.gitignore`**: 이 파일들이 워크트리 안에 생겨 트리를 더럽히므로 저장소 루트 `.gitignore` 에 `.agentdock/` 을 추가했다.

**검증(실측)**

- `.\gradlew.bat test` **182건 통과, 0 실패**. 새 테스트: `DelegationServiceTest`(파일이 실제로 쓰이고 상대 경로 반환·스텝 파일 이름·쓰기 실패 폴백), `DelegationPromptsTest` 확장(지시 파일 절·참조 한 줄·이름 규칙·인라인 폴백). `DelegationAnswerResumeTest` 는 답 문맥이 인라인 대신 `<실행id>-step0.md` 로 넘어가도록 고쳤다.
- 8081(백엔드)로 프로젝트 6(마스터 13)에 명령을 보내 실제 확인(task 39 / 루트 실행 98, 자식 99·100). 워크트리는 `C:\Users\chey.kim\Documents\GitHub\AgentDock-wt\98`.
  - **지시 파일**: `...\AgentDock-wt\98\.agentdock\prompts\` 에 `98-step1.md`(52B)·`98-step2.md`(154B)·`99.md`(354B)·`100.md`(447B) 생성. `99.md` 는 `# 위임 지시` / `## 원래 요청` / `## 맡은 일` / `## 기대 결과` 절을 담았다.
  - **DB 프롬프트(짧아짐)**: 98(루트, 원래 요청)=49자, **99=79자, 100=88자** — 자식 프롬프트가 `지시 파일: .agentdock/prompts/99.md — … 기대 결과: 위임 수신 확인 한 줄` 만 남았다(예전에는 원래 요청·맡은 일·기대 결과·스키마가 다 인라인됐다).
  - **SYSTEM 로그**: 98 → `⎿ 지시 파일: .agentdock/prompts/98-step1.md (30자)`, `⎿ 지시 파일: .agentdock/prompts/98-step2.md (72자)`; 99 → `⎿ 지시 파일: .agentdock/prompts/99.md (162자)`, `⎿ 프롬프트 1,673자`; 100 → `⎿ 지시 파일: .agentdock/prompts/100.md (199자)`.
  - 루트 98 은 `SUCCEEDED`, 자식 99·100 은 `FAILED`(모델이 리더에게 순환 위임을 시도해 거부됨 — 이번 변경과 무관). 루트 `merge_status=NONE`(변경 없음) 이라 **메인 저장소에 병합 커밋이 생기지 않았다**.
  - 정리: 워크트리 `AgentDock-wt\98` + 브랜치 `agentdock/exec-98` 삭제, 검증 행(execution 98~100, 로그 49줄, task 39) 삭제. 사용자의 다른 워크트리(51·52·68·69·74·87)와 대기 중 실행(48·65·66)은 건드리지 않았고, 8081 서버는 종료했다.

**남은 한계(설계대로)**

- 루트 실행의 지시(사용자 명령)는 짧으므로 인라인을 유지한다 — 파일로 옮기는 대상은 **자식 지시와 재판단 문맥**뿐이다.
- 지시 파일은 워크트리에 남는다(정리는 사람이 `DELETE /executions/{id}/worktree`). `.agentdock/` 이 `.gitignore` 에 있어 병합을 막지 않는다.

