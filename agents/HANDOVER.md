# 인수인계 문서 (2026-09-29)

이 문서는 다음 작업을 이어받는 에이전트(Claude Code 등)를 위한 현재 상태 스냅샷이다. 작업 방식·구조·규칙은 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)가 기준 문서이므로 그것을 먼저 읽고, 이 문서는 "지금 어디까지 됐고 다음에 뭘 할지"만 다룬다.

## 1. 먼저 읽을 문서 (순서대로)

1. `agents/CONVENTIONS.md` — 스택, 모듈 구조, DB 스키마, 실행 방법, 포트 규칙, 브랜치/커밋 규칙의 단일 기준 문서
2. `.superpowers/sdd/2026-09-28-java-spring-react-migration/progress.md` — 실제 작업 장부. 각 단계의 결정 사항, 검증 결과, 발견해서 고친 결함, 남긴 제약이 시간순으로 기록돼 있다. **gitignore 대상이라 커밋에는 없고 로컬에만 있다.**
3. `docs/superpowers/specs/2026-09-28-java-spring-react-migration-brief.md` — 스택 전환 브리프(설계 결정 확정본)
4. `docs/superpowers/plans/2026-09-28-java-spring-react-migration.md` — 스택 전환 실행 계획(Task 1~18)
5. `docs/superpowers/specs/2026-09-29-agent-connection-settings-design.md` — Agent 연결 설정 재구성 설계(확정 결정 표 포함)
6. `docs/superpowers/plans/2026-09-29-agent-connection-settings.md` — 위 설계의 실행 계획(Task 1~9, 이번 작업에서 구현 완료)
7. 이 저장소를 만든 최초 이슈 본문(Multi-Agent Organization Platform - Product & Architecture Draft) — 전체 제품 스펙. Phase 구분과 최종 목표는 여기 있다.

## 2. 브랜치와 현재 상태

브랜치 흐름은 `기능 브랜치 → dev → test → release` 다. `dev`가 통합 브랜치이며 `test`/`release`는 아직 만들지 않았다.

| 구분 | 내용 |
| --- | --- |
| `dev` | `fe94644` — 지금까지의 모든 작업이 squash 병합돼 있다 |
| `feat/group-task` | `7536864` — dev에 병합 완료(PR #2). 브랜치는 남아 있고 삭제해도 무방 |
| `feat/ai-connection` | **현재 작업 브랜치**. 원격에 push 완료, PR은 아직 만들지 않음. AI 연결 상태 확인(probe) + Agent 연결 설정 재구성을 담고 있다 |

이미 병합된 PR:

- PR #1 `[Feat] NestJS/Next.js → Spring Boot + Vite React 스택 전환` — Phase 1 전체를 Java 25 / Spring Boot 4.1.1 / Gradle / JPA + QueryDSL / Flyway 와 Vite + React + React Router 로 1:1 포팅
- PR #2 `[Feat] Project/Group/Task 계층 추가와 Task 실행` — Phase 2. 계층이 `Project → Group → Agent` 로 바뀌고 워크스페이스가 프로젝트로 이동했다

`feat/ai-connection` 에서 한 작업:

- 스키마 V3: `ai_connection.last_checked_at`, `last_error`
- `AiConnectionProbe` / `ProbeRegistry` / `ClaudeCodeProbe` — CLI를 짧은 프롬프트로 한 번 실행해 실제 응답 여부를 확인
- `POST /ai-connections/{id}/check` — 결과를 `status`(`CONNECTED`/`ERROR`), `last_error`, `last_checked_at` 에 저장
- 검증 중 결함 1건 수정: 지연 프록시에서 `getProvider().getKey()` 를 읽어 `LazyInitializationException` → fetch join 조회로 해결
- 스키마 V4: `ai_provider.deleted_at`(논리 삭제) + 부분 유니크 인덱스 `ai_provider_key_active`, Provider 4종과 Connection 시드
- Provider API: 논리 삭제(`DELETE /ai-providers/{id}`, 참조 Agent 의 `connection_id` 를 먼저 NULL 로 만든 뒤 Connection 삭제), Connection 재생성(`POST /ai-providers/{id}/connection`), Connection 삭제(`DELETE /ai-connections/{id}`), Provider 재할당(`PUT /agents/{id}/provider`)
- `AgentAvailability` — Provider 삭제/Connection 상태로 사용 가능 여부를 파생 판정. Agent 응답에 `available`/`unavailableReason` 추가, `ExecutionService` 실행 가드가 같은 규칙으로 **409** 거부(권한 403 검사 직후, Runtime 호출 전)
- 웹 로그인 세션(`provider/login`): `AiLoginCommand`(현재 `claude auth login` 만) + `LoginProcess`/`PipeLoginProcess` + `LoginSessionService`(출력 버퍼 재생, 유휴 5분 종료, Connection 당 1세션) + `LoginController`(SSE)
- 화면: Providers → "Agent 연결 설정" 카드형 화면 + Provider 추가 모달, 로그인 패널, Agents 화면의 사용 불가 배지/Run 비활성/Provider 재할당
- 테스트: JUnit 34건(`AgentAvailability`, 실행 가드, Provider/Connection 서비스, AgentService, 로그인 세션)
- 검증 중 결함 1건 수정: probe 가 출력을 먼저 다 읽어, 프로세스가 끝나지 않고 stdout 도 닫지 않으면 타임아웃이 동작하지 않고 요청이 무한 대기 → 출력을 별도 스레드에서 읽고 `waitFor(30초)` 로 종료를 판정하도록 수정(30.6초 후 `확인 시간 초과 (30초)` 반환 확인)

PR 링크(아직 생성 전):

```
https://github.com/seop-kim/AgentDock/compare/dev...feat/ai-connection?expand=1
제목: [Feat] Agent 연결 설정 재구성(Provider 시드·논리 삭제, 웹 로그인 패널, 연결 상태 종속 실행)
```

## 3. 바로 다음에 할 일 (우선순위 순)

1. **Provider별 probe/Runtime/로그인 명령 추가 (CODEX / GEMINI / COMMAND_CODE)**
   현재 `ClaudeCodeRuntime` / `ClaudeCodeProbe` / `ClaudeCodeLoginCommand` 만 있다. 다른 provider는 실행 시 404(`No AgentRuntime registered for provider`), 연결 확인 시 ERROR("이 Provider 는 아직 연결 확인을 지원하지 않습니다"), 로그인 시 400("로그인 창을 아직 지원하지 않습니다") 로 표시된다.
   주의: 이 머신에는 `codex` / `gemini` / `cmdc` 가 설치돼 있지 않아 **실검증이 불가능**하다. 각 CLI의 비대화형 실행 플래그와 로그인 명령을 문서에서 확인한 뒤 구현하고, 설치 후 검증하는 순서가 맞다. 플래그를 추측해서 넣지 말 것.
2. **`capabilities` 기반 모델/모드 (스펙 5장)**
   `ai_provider.capabilities`(JSONB)는 저장·응답만 되고 읽는 코드가 없다. Provider별 지원 모델/모드를 정의해 API로 노출하고, Agents 화면에서 지원하는 모델/모드만 선택하게 만든다. `Agent.mode` 는 저장되고 `AgentExecutionRequest` 까지 전달되지만 `ClaudeCodeRuntime` 이 아직 사용하지 않으므로 Mode 의미(예: Plan/Execute)도 함께 정의해야 한다.
3. ~~CONNECTED 경로 검증~~ **완료**(2026-09-29) — 사용자가 `claude` 에 재로그인한 뒤, 실제 CLI 로 연결 확인이 `CONNECTED`(성공)로 끝나고 그 Provider 의 Agent 가 `available=true` 로 바뀌는 것을 확인했다. 그 전의 ERROR 경로(OAuth 만료)도 함께 확인했다.
4. ~~로그인 안내 UX~~ **완료**(2026-09-29) — 웹 로그인 패널로 구현했다. 연결이 `ERROR` 면 카드에 "로그인" 버튼이 나타나고, 패널이 `claude auth login` 출력(인증 URL 은 링크로 표시)을 SSE 로 스트리밍하며, 성공(exit 0)하면 자동으로 다시 연결 확인한다. 미지원 Provider 는 사유를 안내한다.
5. **메뉴형 CLI 로그인이 필요해지면 `LoginProcess` PTY 구현** — 지금은 파이프 구현(`PipeLoginProcess`)만 있다. `LoginProcess` 구현체만 교체하면 세션/SSE 코드는 그대로 쓴다.
6. (선택) **OS Credential Store 연동**(스펙 6장 우선순위 2) — 현재는 CLI 세션 위임만 구현돼 있다. 후순위.

Phase 3 이후(Workflow/WorkflowStep 순차·병렬 실행, Shared Context, Message, Artifact, Review, Decision)는 아직 손대지 않았다.

## 4. 이 환경에서 작업할 때 (실측값)

- JDK 25는 `C:\Users\chey.kim\.jdks\openjdk-25.0.2` 에 있고 PATH에 없다. Gradle 호출 시 `JAVA_HOME` 을 지정해야 한다.
  ```powershell
  $env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
  ```
- DB는 PostgreSQL 17, DB명 `AGENT_DOCK`, 계정 `postgres`, 비밀번호 없음. `psql` 은 PATH에 없으므로 전체 경로를 쓴다: `C:\Program Files\PostgreSQL\17\bin\psql.exe`
- 백엔드 기동은 `apps/backend` 에서 `gradlew.bat bootRun`. 프론트는 루트에서 `npm run dev:frontend`(3030) 또는 `:test`(3031)
- **검증 포트 규칙**: 사용자 개발 포트는 백엔드 8080 / 프론트 3030 이다. 에이전트가 검증할 때는 8081 / 3031 을 쓰고, 끝나면 즉시 종료한다.
- 프론트를 8081 백엔드에 붙일 때는 `VITE_API_BASE=http://localhost:8081` 을 넘긴다.
- 브라우저 검증 도구로 `agent-browser`(0.38.1 + Chrome 154, `~/.agent-browser`)가 설치돼 있다. 세션을 나눠 쓰고 끝나면 `close` 한다. `snapshot -i` 가 이 앱에서 느릴 때가 있으니 `eval` 로 DOM 을 직접 읽는 편이 빠르다(카드 클래스는 `div[class*=_card_]`).
- `gh` CLI 는 없다. PR 생성·병합은 GitHub 웹에서 한다.
- 테스트로 만든 데이터는 검증 후 TRUNCATE 한다(`flyway_schema_history` 는 유지). 현재 Flyway 는 v1~v4 까지 적용돼 있고, V4 의 시드로 Provider 4종(CLAUDE_CODE/CODEX/COMMAND_CODE/GEMINI)과 각 Connection 1개가 들어 있다. 시드 직후 status 는 `DISCONNECTED` 이므로 화면에서 "연결"을 한 번 눌러야 Agent 가 실행된다. Agent/Project/Execution 등 나머지 테이블은 비어 있다.
- Provider 를 시드 상태로 되돌리려면(검증 중 논리 삭제했다면):
  ```powershell
  & 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -U postgres -d AGENT_DOCK -c "UPDATE ai_provider SET deleted_at=NULL WHERE deleted_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ai_provider p2 WHERE p2.key=ai_provider.key AND p2.deleted_at IS NULL);"
  ```

## 5. 반복해서 밟은 함정 (중요)

- **`gradlew bootRun` 이 실패해도 BUILD SUCCESSFUL 로 보인다.** spring-boot-devtools 의 `RestartLauncher` 때문이다. 컴파일 통과만으로 끝내지 말고 반드시 기동 로그(`Started AgentDockApplication`)나 실제 HTTP 응답을 확인한다. 이 함정 때문에 컨텍스트 기동 실패를 놓칠 뻔했다.
- **`spring.jpa.open-in-view: false`** 다. 서비스에서 엔티티의 LAZY 관계를 그냥 읽으면 `LazyInitializationException` 이 난다. fetch join 조회를 쓰거나, 오래 걸리는 작업(예: CLI probe)에는 `@Transactional` 로 감싸지 말고 조회 시점에 함께 읽는다.
- **shadow FK 필드**(`insertable = false, updatable = false`)는 저장 직후 엔티티에서 비어 있다. 생성 응답에서 FK id 가 필요하면 관계에서 보완하는 기존 패턴(`ExecutionResponse.from` 등)을 따른다.
- 응답 DTO 는 엔티티를 직접 반환하지 않는다(`record` Response DTO). 양방향 연관관계 때문에 Jackson 순환이 생긴다.
- `POST` 는 201 을 반환한다(`@ResponseStatus(HttpStatus.CREATED)`). 기존 NestJS 동작과 맞춘 것이다.
- **삭제 API 는 204(본문 없음)** 다. 프론트 `request()` 는 `res.json()` 전에 204 를 처리해 `undefined` 를 반환해야 한다.
- **로그인 CLI 가 띄운 자식 프로세스(브라우저 등)가 파이프를 잡고 있으면 stdout 에 EOF 가 오지 않는다.** 종료 판정은 `Process.onExit()` 로 하고 남은 출력은 잠깐만 기다린다(`PipeLoginProcess`).
- **probe 에서 출력을 먼저 다 읽으면 타임아웃이 동작하지 않는다.** 프로세스가 끝나지 않으면 `readAllBytes` 가 영원히 블록돼 HTTP 요청이 무한 대기한다. 출력은 별도 스레드에서 읽고 `waitFor(30초)` 로 종료를 판정한다(`ClaudeCodeProbe`).
- **PowerShell 에서 `psql` 에 한글 리터럴을 넘기면 인코딩 오류로 명령이 실패한다**(`"UTF8" 인코딩에서 사용할 수 없는 문자`). 검증용 SQL 은 ASCII 로 쓰거나 파라미터로 넘긴다.
- **`ProcessBuilder` 는 `.cmd`/`.bat` 을 직접 실행하지 못한다.** 대체 CLI 로 성공 경로를 흉내내려면 진짜 실행 파일이 필요하다. 이 머신의 Windows 내장 exe 들은 인자를 거부해 exit≠0 이므로, 성공 경로는 실제 `claude` 로 확인하고 실패/타임아웃 경로는 `CLAUDE_CODE_BIN` 을 다른 실행 파일로 바꿔 확인했다.
- 검증용 스크립트에서 PowerShell 과 `curl.exe` 를 섞어 쓸 때 JSON 인용부호가 벗겨지는 문제가 있었다. `Invoke-WebRequest` 에 body 를 넘기는 방식을 쓰는 편이 안전하다.

## 6. 작업 방식 (기존 관례)

- 기능 브랜치 커밋은 Conventional Commits(`feat: ...`, `fix: ...`), `dev` 에 반영할 때는 squash merge 하고 그 커밋 메시지(PR 제목)를 `[Type] 제목` 형식으로 쓴다. 자세한 규칙은 `agents/CONVENTIONS.md` 의 "브랜치 흐름과 커밋 컨벤션" 참고.
- `Co-Authored-By:` 트레일러는 붙이지 않는다.
- 한 PR 은 하나의 논리적 단위로 유지한다.
- 구조나 컨벤션을 바꾸면 `agents/CONVENTIONS.md` 도 함께 갱신한다.
- 권한은 Prompt 설명이 아니라 백엔드에서 실제로 차단한다(`PermissionService.isAllowed`, `terminalExecute` 없으면 실행 전 403). 연결 상태도 같은 방식으로 백엔드에서 차단한다(409).
- Agent(논리적 직원)와 Runtime(실행 엔진)은 분리한다. 새 Runtime/Probe/로그인 명령은 인터페이스만 구현하고 등록하면 나머지 코드는 건드리지 않는다.
