# 인수인계 문서 — 구현(백엔드/프론트) 단계 (2026-09-29, **일시 중단**)

> **이 문서는 목업을 시작하기 전의 구현 단계 스냅샷이다. 현재 진행 중인 작업(목업 단계)은 [`HANDOVER.md`](./HANDOVER.md) 를 본다.** 목업이 확정되기 전에는 아래 "지금 바로 이어서 할 일"을 진행하지 않는다. 브랜치·커밋 상태(`feat/workspace-runtime` 등)는 그 시점 기준이므로 실제 저장소 상태는 `git log`/`git branch` 로 다시 확인한다.

이 문서는 다음 작업을 이어받는 에이전트(Claude Code 등)를 위한 현재 상태 스냅샷이다. 작업 방식·구조·규칙은 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)가 기준 문서이므로 그것을 먼저 읽고, 이 문서는 "지금 어디까지 됐고 다음에 뭘 할지"만 다룬다.

## 0. 지금 바로 이어서 할 일 (가장 먼저 볼 것)

1. **Stage 2 — 화면 재배치** (아래 §3). 사용자가 바로 요청했던 **CLI 확인 창은 완료**했다(§2 (C)).
2. 그다음 Stage 3~4 (아래 §3).

## 1. 먼저 읽을 문서 (순서대로)

1. `agents/CONVENTIONS.md` — 스택, 도메인 구조, 모듈, DB 스키마, 실행 방법, 포트 규칙, 브랜치/커밋 규칙의 기준 문서
2. `docs/superpowers/plans/2026-09-29-workspace-runtime-restructure.md` — 현재 작업(Stage 1) 계획 + Stage 2~4 예고
3. `docs/superpowers/plans/2026-09-29-agent-model-mode-capabilities.md` — PR #4(모델/모드 capabilities) 계획
4. `docs/superpowers/plans/2026-09-29-agent-connection-settings.md`, `docs/superpowers/specs/2026-09-29-agent-connection-settings-design.md` — PR #3 설계/계획
5. `.superpowers/sdd/2026-09-28-java-spring-react-migration/progress.md` — 스택 전환기 장부(로컬 전용, gitignore)
6. 이 저장소를 만든 최초 이슈 본문(Multi-Agent Organization Platform) — 전체 제품 스펙

## 2. 브랜치와 현재 상태

브랜치 흐름은 `기능 브랜치 → dev → test → release`. **병합이 끝난 기능 브랜치는 로컬·원격 모두 삭제한다.**

| 구분 | 내용 |
| --- | --- |
| `dev` | `e28bf4f` — PR #4 까지 squash 병합 |
| `feat/workspace-runtime` | **현재 작업 브랜치**. Stage 1(워크스페이스 중심 재편) + CLI 설치 흐름. PR 미생성 |

병합된 PR: #1 스택 전환 / #2 Project·Group·Task / #3 Agent 연결 설정 재구성 / #4 모델·모드 capabilities.

### 이 브랜치에서 한 작업

**(A) Stage 1 — 워크스페이스 중심 재편** (커밋 `05b44ee`, `3b6e5c8`, `d100ae0`, `580b48c`, `cf8bda9`, `6c047c0`, `489032c`)

- 스키마 V6(`ai_provider.enabled`, `workspace_runtime_status`) / V7(`project_workspace` N:N + `agent.project_id`·`persona`, `project.workspace_id` 제거) / V8(`agent.connection_id`·`ai_connection` 제거)
- 런타임 on/off(`PUT /ai-providers/{id}/enabled`), 폴더별 런타임 상태(`GET /workspaces/{id}/runtimes`, `POST /workspaces/{id}/runtimes/{providerId}/check`, probe 는 **cwd=폴더**)
- 프로젝트↔워크스페이스 N:N(기본 1개), 에이전트는 프로젝트 소속 + 페르소나(`--append-system-prompt` 로 전달)
- 실행 가드: 런타임 살아있음 + enabled + 프로젝트 기본 워크스페이스에서 `CONNECTED` → 아니면 409
- E2E 검증: 폴더별 상태 분리 기록, 페르소나가 CLI 까지 전달(로그에 `BANANA`), 런타임 off 시 409

**(B) CLI 설치 흐름 + 실행 파일 해석** (이번 커밋들)

- **실행 파일 해석**(`process/Executables.java`): Java 는 `npm` 처럼 확장자 없이 쓰는 이름을 `npm.cmd` 로 찾지 못한다(`CreateProcess error=2`). PATH/PATHEXT 를 직접 훑어 실제 파일을 찾아 실행한다 → `ProcessService`(에이전트 실행)와 `ClaudeCodeProbe` 에 적용. **셸을 경유하지 않으므로 프롬프트가 셸로 새지 않는다.**
- **CLI 없음 감지**: `ProbeResult.cliMissing` + `workspace_runtime_status.cli_missing`(V9). 화면에서 "CLI 설치" 버튼을 띄우는 근거.
- **설치 계획(데이터)**: `capabilities` 에 `install`(단계 배열), `installRequire`(먼저 있어야 하는 실행 파일, 예: npm), `installPrerequisite`(없을 때 먼저 실행할 단계, 예: `nvm install lts`). V9 가 4종 런타임에 시드(설치 계획이 없거나 예전 문자열 형식일 때만 변환).
- **설치/로그인 세션**: `POST /ai-providers/{id}/install`(+`/login`), `GET|POST|DELETE /ai-providers/command-sessions/{sessionId}/...`(SSE). `LoginSessionService` 가 단계 목록을 만들어 순서대로 실행하고, 필수 도구가 없으면 **선행 단계를 먼저** 실행한다. 각 단계는 `CommandShell`(`cmd.exe /c`, 그 외 `sh -c`)로 감싼다 — 설치만 셸을 쓴다.
- 화면: 에이전트 설정 카드에 ON/OFF + **CLI 설치** + 로그인 + 설치 계획 편집(모델/모드/설치 단계/필수 도구), 워크스페이스 런타임 행에 **CLI 설치(또는 설치/재설치)** 버튼과 출력 패널(성공 시 자동 재확인). `LoginPanel` → **`CommandPanel`**(로그인/설치 공용)로 일반화.
- E2E 검증: `npm view @openai/codex version` → `0.159.0` exit 0(셸 경유로 npm 실행됨), 필수 도구가 없을 때 `prerequisite-ran` → `install-step-ran` 순서로 실행되고 exit 0. JUnit 54건, 프론트 tsc+build 통과.

**(C) CLI 확인 창** (이번 커밋들)

- **CLI 상태 API**: `GET /ai-providers/{id}/cli` → `{resolvedPath, version, runnable, detail}`. `Executables.locate`(PATH/PATHEXT 로 실제 파일 경로, 없으면 null)로 찾고, 있으면 `<실행 파일> --version` 을 10초 제한으로 실행해 첫 줄을 읽는다(`CliStatusService`, 셸 미경유, 저장 안 함). 런타임별 `AiRuntimeCli`/`CliRegistry` 구현이 등록된 런타임만 경로/버전을 보여 준다(현재 Claude 만; 나머지는 "CLI 정보를 확인할 수 없습니다").
- **CLI 확인 창**(`CliPanel.tsx`): CLI 를 쓰는 모든 자리(에이전트 설정 카드 · 워크스페이스 런타임 행 · 에이전트 목록)에서 `CLI 확인` 버튼 **하나**로 연다. 창에서 경로/버전/실행 가능 여부 + 이 폴더에서 확인(probe) + 로그인 + 설치를 모두 하고, 출력은 기존 `CommandPanel`(SSE)을 재사용한다. 각 자리에 흩어져 있던 인라인 버튼(로그인/CLI 설치/이 폴더에서 확인)은 창 안으로 모았다.
- E2E 검증: `GET /ai-providers/12/cli` → `C:\Users\chey.kim\.local\bin\claude.exe`, `2.1.227 (Claude Code)`, `runnable=true`. COMMAND_CODE → "CLI 정보를 확인할 수 없습니다", 없는 id → 404. 화면 3곳에서 창이 열리고, 창의 "이 폴더에서 확인"이 표(상태 CONNECTED, 마지막 확인 갱신)를 다시 읽는다. JUnit 59건, 프론트 tsc+build 통과.
  - **미실행(의도)**: 로그인/설치 버튼의 실제 실행은 인증 상태·전역 설치를 건드려 이번 검증에서 누르지 않았다. `CommandPanel` 자체는 변경하지 않았고(부모만 교체), 직전 커밋에서 검증된 코드다.

## 3. 다음에 할 일 (우선순위)

1. **Stage 2 — 화면 재배치**: "에이전트 설정" 라우트/파일 정리, Agents 는 읽기 목록 중심 + 생성은 프로젝트 상세로 이동, 그리고 **모델/권한을 CLI 목록에서 고르는 UI**(`~/.claude.json` 의 `additionalModelOptionsCache` = `/model` 이 보여주는 라벨·설명·disabled, `claude --help` 의 `--permission-mode` 6종) 를 흡수. 사용자가 "텍스트로 주면 쓰이는지 알 수 없다"고 한 지적의 해결책.
2. **Stage 3 — 프로젝트 상세 채팅(자동 라우팅)**: 채팅 입력 → 그룹 리더/에이전트로 라우팅 → Task/Execution + SSE. 에이전트가 여러 개일 때 규칙 확정. 필요하면 `task.workspace_id` 추가.
3. **Stage 4 — Tasks 통합 화면**: 전 프로젝트 진행/종료 작업 + 필터.
4. **다른 런타임(CODEX/GEMINI/COMMAND_CODE) probe/Runtime/로그인 명령** — CLI 미설치로 실검증 불가. 설치 흐름으로 설치한 뒤 문서 확인해서 구현(플래그 추측 금지). CLI 상태 조회도 `AiRuntimeCli` 구현을 추가하면 자동으로 잡힌다.
5. 메뉴형 CLI 가 필요해지면 `LoginProcess` PTY 구현, (선택) OS Credential Store.

## 4. 이 환경에서 작업할 때 (실측값)

- JDK 25: `C:\Users\chey.kim\.jdks\openjdk-25.0.2` (PATH 밖) → `$env:JAVA_HOME` 지정 후 `gradlew.bat`.
- DB: PostgreSQL 17, `AGENT_DOCK`, `postgres`, 비밀번호 없음. `psql` 전체 경로 `C:\Program Files\PostgreSQL\17\bin\psql.exe`.
- 검증 포트: 백엔드 8081 / 프론트 3031 (사용자 포트 8080/3030 금지). 프론트는 `VITE_API_BASE=http://localhost:8081`.
- **사용자는 자기 앱을 8080/3030 으로 띄워 둔다.** 코드를 바꾸면 그 앱을 **재시작해야** 반영된다(V9 마이그레이션도 그때 적용).
- 이 머신의 CLI: `claude` = `C:\Users\chey.kim\.local\bin\claude.exe`, `node` = `C:\nvm4w\nodejs\node.exe`, `npm` = `C:\nvm4w\nodejs\npm.cmd`(+`npm.ps1`), `nvm` 1.2.2(있음), `cmdc`/`codex`/`gemini` 없음, **`winget` 없음**.
- `agent-browser`(0.38.1 + Chrome) 있음. `open`/`eval`/`snapshot` 이 자주 멈추니 **스크린샷 + 이미지 확인** 위주로.
- `gh` CLI 없음 → PR 생성/병합은 GitHub 웹.
- 현재 DB: Flyway **v1~v9**, `ai_provider` 4종(CLAUDE_CODE·COMMAND_CODE enabled=true, CODEX/GEMINI 는 논리 삭제). 테스트용으로 workspace(`agentDock`)·project(`test`, 기본 워크스페이스 1개)·agent(`test`, CLAUDE_CODE) 1건이 있다.
- 검증 정리 SQL(순서 중요): `execution_log → task → execution → agent_group_member → agent_group → agent → project_workspace → project → workspace → permission_profile → agent_role`.

## 5. 반복해서 밟은 함정 (중요)

- **`gradlew bootRun` 이 실패해도 BUILD SUCCESSFUL 처럼 보인다**(devtools `RestartLauncher`). 기동 로그(`Started AgentDockApplication`)나 HTTP 응답으로 확인한다.
- **`spring.jpa.open-in-view: false`**: 트랜잭션 밖 LAZY 접근은 `LazyInitializationException`. fetch join 사용. 오래 걸리는 작업(probe/세션)에 `@Transactional` 금지.
- **Java 는 `npm`(확장자 없는 이름)을 직접 실행하지 못한다.** `Cannot run program "npm": CreateProcess error=2`. 반면 **전체 경로 `npm.cmd` 는 실행된다.** 그래서 `Executables.resolve` 로 PATH/PATHEXT 를 훑어 실제 파일을 찾는다. 셸로 감싸는 방법도 되지만(`cmd.exe /c`) 그건 설치 단계에만 쓴다(프롬프트가 흐르는 실행 경로는 셸 금지).
- **이미 적용된 Flyway 마이그레이션 파일을 수정하면 체크섬 불일치로 기동이 실패한다**(`Migration checksum mismatch for migration version 9`). 이번에 실제로 밟았다. 해결: ① 그 마이그레이션을 **멱등하게** 다시 쓴다(`ADD COLUMN IF NOT EXISTS`, 조건부 UPDATE), ② `DELETE FROM flyway_schema_history WHERE version='9';` 로 이력을 지우고 재기동.
- **`agent` 테이블에 행이 있으면 V7 이 실패**한다(`project_id NOT NULL`). 이관 전에 비워야 한다.
- **`Project.workspace` 제거 후 남은 JPQL**(`TaskRepository.RELATIONS` 의 `join fetch p.workspace`, `where p.id`)을 함께 고쳐야 한다. 엔티티만 고치면 기동 시 `UnknownPathException`.
- **cmd 의 코드페이지 때문에 한글 `echo` 출력이 SSE 에서 깨진다.** 설치 단계의 안내 문구는 ASCII 로 쓴다.
- shadow FK 필드는 저장 직후 비어 있다(생성 응답은 관계로 보완 또는 커밋 후 재조회). 응답 DTO 는 엔티티 대신 record. POST 201, 삭제 204(프론트 `request()` 가 204 처리).
- probe 는 출력을 별도 스레드에서 읽고 `waitFor(30초)` 로 종료 판정(먼저 다 읽으면 타임아웃이 동작하지 않음). 로그인/설치 세션은 `Process.onExit()` 로 종료 감지.
- **PowerShell**: `psql` 에 한글 리터럴을 넘기면 인코딩 오류 → UTF-8 파일 + `psql -f`. `Invoke-WebRequest` 는 UTF-8 JSON 을 깨져 보여줄 수 있음(데이터 문제 아님, DB 는 psql 로 확인). 명령이 너무 길면 "입력이 너무 깁니다"로 잘리니 E2E 는 단계로 나눈다.
- 편집 도구가 "파일이 수정됐다"고 거부하면(OneDrive 동기화 등) 다시 읽고 전체 내용을 쓴다.

## 6. 작업 방식 (기존 관례)

- 기능 브랜치 커밋은 Conventional Commits, `dev` 는 squash merge + `[Type] 제목`. `Co-Authored-By:` 금지. **소단위 커밋**으로 나눈다. 병합 후 브랜치 정리.
- 구조/컨벤션을 바꾸면 `agents/CONVENTIONS.md` 도 함께 갱신한다.
- 권한(403)·런타임 enabled·폴더 상태(409)는 백엔드에서 강제한다.
- Agent(논리적 직원)와 Runtime(실행 엔진)은 분리한다. 새 Runtime/Probe/로그인 명령은 인터페이스만 구현하고 등록한다.
- **자주 바뀌는 값은 코드에 넣지 않는다.** 모델/모드 목록과 설치 계획은 데이터(화면 편집)로 두고 최종 판단은 CLI 에 위임한다.
