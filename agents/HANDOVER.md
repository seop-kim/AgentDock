# 인수인계 문서 (2026-09-29)

이 문서는 다음 작업을 이어받는 에이전트(Claude Code 등)를 위한 현재 상태 스냅샷이다. 작업 방식·구조·규칙은 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)가 기준 문서이므로 그것을 먼저 읽고, 이 문서는 "지금 어디까지 됐고 다음에 뭘 할지"만 다룬다.

## 1. 먼저 읽을 문서 (순서대로)

1. `agents/CONVENTIONS.md` — 스택, 도메인 구조, 모듈, DB 스키마, 실행 방법, 포트 규칙, 브랜치/커밋 규칙의 단일 기준 문서
2. `.superpowers/sdd/2026-09-28-java-spring-react-migration/progress.md` — 스택 전환기의 작업 장부(로컬 전용, gitignore)
3. `docs/superpowers/specs/2026-09-28-java-spring-react-migration-brief.md`, `docs/superpowers/plans/2026-09-28-java-spring-react-migration.md` — 스택 전환 기록
4. `docs/superpowers/specs/2026-09-29-agent-connection-settings-design.md`, `docs/superpowers/plans/2026-09-29-agent-connection-settings.md` — PR #3 의 설계/계획
5. `docs/superpowers/plans/2026-09-29-agent-model-mode-capabilities.md` — PR #4 의 계획
6. `docs/superpowers/plans/2026-09-29-workspace-runtime-restructure.md` — **현재 작업(Stage 1)의 계획과 Stage 2~4 예고**
7. 이 저장소를 만든 최초 이슈 본문(Multi-Agent Organization Platform) — 전체 제품 스펙

## 2. 브랜치와 현재 상태

브랜치 흐름은 `기능 브랜치 → dev → test → release` 다. **병합이 끝난 기능 브랜치는 로컬·원격 모두 삭제한다.**

| 구분 | 내용 |
| --- | --- |
| `dev` | `e28bf4f` — PR #4 까지 squash 병합 |
| `feat/workspace-runtime` | **현재 작업 브랜치**(dev 에서 분기). Stage 1: 런타임 on/off + 폴더별 상태 + 프로젝트 N:N 워크스페이스 + 프로젝트 소속 에이전트/페르소나 + Connection 제거. PR 미생성 |

이미 병합된 PR:

- PR #1 `[Feat] NestJS/Next.js → Spring Boot + Vite React 스택 전환`
- PR #2 `[Feat] Project/Group/Task 계층 추가와 Task 실행`
- PR #3 `[Feat] Agent 연결 설정 재구성(Provider 시드·논리 삭제, 웹 로그인 패널, 연결 상태 종속 실행)`
- PR #4 `[Feat] Provider 모델/모드 목록(capabilities)과 CLI 플래그 전달`

`feat/workspace-runtime` 에서 한 작업(Stage 1):

- 스키마: V6(`ai_provider.enabled`, `workspace_runtime_status`), V7(`project_workspace` N:N + `agent.project_id`/`persona`, `project.workspace_id` 제거), V8(`agent.connection_id` + `ai_connection` 제거)
- 런타임 on/off: `PUT /ai-providers/{id}/enabled`
- 폴더별 상태: `GET /workspaces/{id}/runtimes`, `POST /workspaces/{id}/runtimes/{providerId}/check` — probe 가 **cwd=폴더**로 CLI 를 실행(`AiConnectionProbe` → `AiRuntimeProbe`, `check(String cwd)`)
- 로그인 세션을 런타임 기준으로 이동: `POST /ai-providers/{id}/login` + `/ai-providers/login-sessions/...`
- 프로젝트↔워크스페이스 N:N(기본 1개): `POST/DELETE /projects/{id}/workspaces[/{workspaceId}]`
- 에이전트: `projectId`(필수) + `persona` 추가, `connectionId` 제거. 목록에 프로젝트 정보 포함
- 실행 가드: **런타임 살아있음 + enabled + 프로젝트 기본 워크스페이스에서 CONNECTED** → 아니면 409(사유 3종). 작업 디렉터리는 프로젝트 기본 워크스페이스(할당 없으면 409)
- `ClaudeCodeRuntime.buildArgs`: persona → `--append-system-prompt`(신규), mode → `--permission-mode`, model → `--model`
- 화면: 에이전트 설정(런타임 ON/OFF + 로그인), 워크스페이스(폴더별 런타임 상태 표 + "이 폴더에서 확인"), 프로젝트(워크스페이스 다중 할당), 에이전트(프로젝트 선택 + 페르소나 + 켜둔 런타임만)
- 테스트: JUnit 45건 통과, 프론트 tsc+build 통과
- E2E 검증: 폴더 확인 CONNECTED/DISCONNECTED 분리 기록, 프로젝트 2개 워크스페이스(첫 할당 기본), 에이전트 실행 `SUCCEEDED(exit 0)` + **로그에 페르소나 결과(`BANANA`) 확인**, 런타임 off 시 실행 409

PR 링크(아직 생성 전):

```
https://github.com/seop-kim/AgentDock/compare/dev...feat/workspace-runtime?expand=1
제목: [Feat] 워크스페이스 중심 재편(런타임 on/off, 폴더별 상태, 프로젝트 N:N 워크스페이스, 에이전트 페르소나)
```

## 3. 바로 다음에 할 일 (우선순위 순)

1. **Stage 2 — 화면 재배치** (계획 §Stage 2)
   - "에이전트 설정" 라우트/파일 정리, Agents 는 읽기 목록 중심으로 바꾸고 에이전트 생성은 프로젝트 상세로 이동
   - **모델/권한 옵션 목록 UI** 를 여기서 흡수: `~/.claude.json` 의 `additionalModelOptionsCache`(라벨/설명/disabled 포함 — `/model` 이 보여주는 그 목록)와 `claude --help` 의 `--permission-mode` 6종을 읽어 화면에 **선택 목록**으로 표시한다. 자유 텍스트 대신 목록에서만 고르게 하고, 사용 불가(disabled) 항목은 사유와 함께 비활성 표시. 사용자가 "텍스트로 주면 쓰이는지 알 수 없다"고 한 지적의 해결책이다.
2. **Stage 3 — 프로젝트 상세 채팅(자동 라우팅)**: 채팅 입력 → 그룹 리더/그룹 없는 에이전트로 라우팅 → Task/Execution + SSE. 에이전트가 여러 개일 때 규칙은 이때 확정. `task.workspace_id` 도 이 단계에서 필요하면 추가.
3. **Stage 4 — Tasks 통합 화면**: 전 프로젝트 진행/종료 작업 목록 + 필터.
4. **Provider별 probe/Runtime/로그인 명령 (CODEX / GEMINI / COMMAND_CODE)** — 이 머신에 CLI 가 없어 **실검증 불가**. 문서로 플래그를 확인한 뒤 구현하고 설치 후 검증한다(추측 금지).
5. **메뉴형 CLI 로그인이 필요해지면 `LoginProcess` PTY 구현**, (선택) OS Credential Store.

## 4. 이 환경에서 작업할 때 (실측값)

- JDK 25는 `C:\Users\chey.kim\.jdks\openjdk-25.0.2` (PATH 밖) → Gradle 호출 시 `JAVA_HOME` 지정
  ```powershell
  $env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
  ```
- DB: PostgreSQL 17, `AGENT_DOCK`, `postgres`, 비밀번호 없음. `psql` 전체 경로: `C:\Program Files\PostgreSQL\17\bin\psql.exe`
- 검증 포트: 백엔드 8081 / 프론트 3031 (사용자 포트 8080/3030 금지). 프론트는 `VITE_API_BASE=http://localhost:8081`
- `agent-browser`(0.38.1 + Chrome) 설치됨. **`open`/`eval`/`snapshot` 이 자주 멈춘다** → 스크린샷 + 이미지 확인 위주로 하고, 명령은 짧게 끊어 실행한다.
- `gh` CLI 없음 → PR 생성/병합은 GitHub 웹에서.
- 현재 DB 상태: Flyway **v1~v8** 적용, `ai_provider` 4종 시드(CLAUDE_CODE 만 `enabled=true`), 나머지 테이블 비어 있음. 폴더별 상태(`workspace_runtime_status`)는 워크스페이스를 지우면 CASCADE 로 함께 사라진다.
- 검증 후 정리 SQL(순서 중요):
  ```sql
  DELETE FROM execution_log; DELETE FROM task; DELETE FROM execution;
  DELETE FROM agent_group_member; DELETE FROM agent_group; DELETE FROM agent;
  DELETE FROM project_workspace; DELETE FROM project; DELETE FROM workspace;
  DELETE FROM permission_profile; DELETE FROM agent_role;
  ```
- 논리 삭제된 런타임 복구:
  ```powershell
  & 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -U postgres -d AGENT_DOCK -c "UPDATE ai_provider SET deleted_at=NULL WHERE deleted_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ai_provider p2 WHERE p2.key=ai_provider.key AND p2.deleted_at IS NULL);"
  ```

## 5. 반복해서 밟은 함정 (중요)

- **`gradlew bootRun` 이 실패해도 BUILD SUCCESSFUL 로 보인다**(devtools `RestartLauncher`). 기동 로그(`Started AgentDockApplication`)나 HTTP 응답으로 확인한다.
- **`spring.jpa.open-in-view: false`**: LAZY 관계를 트랜잭션 밖에서 읽으면 `LazyInitializationException`. fetch join 또는 조회 시점 함께 읽기. 오래 걸리는 작업(probe/로그인)에 `@Transactional` 금지.
- **`agent` 테이블에 행이 있으면 V7 이 실패한다**(`project_id NOT NULL` 추가). 이관 전에 반드시 비워야 한다(마이그레이션은 실패 시 롤백되므로 재시도 가능). 이번에 실제로 밟았다.
- **`Project.workspace` 제거 후 남은 JPQL** 을 찾아 고쳐야 한다(`TaskRepository.RELATIONS` 의 `join fetch p.workspace`, `where p.id`). 엔티티만 고치고 쿼리를 놓치면 기동 시 `UnknownPathException` 이 난다.
- **shadow FK 필드**는 저장 직후 비어 있다. 생성 응답은 관계로 보완하거나 커밋 후 재조회.
- 응답 DTO 는 엔티티를 직접 반환하지 않는다(Jackson 순환). `POST` 201, 삭제 204(프론트 `request()` 가 204 처리).
- **probe 에서 출력을 먼저 다 읽으면 타임아웃이 동작하지 않는다.** 출력은 별도 스레드에서 읽고 `waitFor(30초)` 로 종료 판정(`ClaudeCodeProbe`). 로그인도 파이프 EOF 대신 `Process.onExit()` 로 종료를 감지한다(`PipeLoginProcess`).
- **PowerShell 에서 `psql` 에 한글 리터럴을 넘기면 인코딩 오류로 실패**한다. UTF-8 파일 + `psql -f` 를 쓴다. `Invoke-WebRequest` 는 UTF-8 JSON 을 깨져 보여 줄 수 있으니(데이터 문제 아님) DB 는 `psql` 로 확인한다.
- **PowerShell 명령이 너무 길면 잘린다**("입력이 너무 깁니다"). E2E 스크립트는 단계로 나눠 실행한다.
- **`ProcessBuilder` 는 `.cmd`/`.bat` 을 실행하지 못한다.** 성공 경로는 실제 `claude` 로 검증한다.
- **`claude --help` 로 확인한 사실**: `--model`, `--permission-mode`(6종), `--append-system-prompt` 가 있고 모델 목록 명령은 없다. `~/.claude.json` 의 `additionalModelOptionsCache` 에 `/model` 목록(라벨·설명·disabled)이 캐시된다.
- 편집 도구가 "파일이 수정됐다"고 거부하면(OneDrive 동기화) 다시 읽고 전체 내용을 쓴다.

## 6. 작업 방식 (기존 관례)

- 기능 브랜치 커밋은 Conventional Commits, `dev` 는 squash merge + `[Type] 제목`. `Co-Authored-By:` 금지. 병합 후 브랜치 정리.
- 구조/컨벤션을 바꾸면 `agents/CONVENTIONS.md` 도 함께 갱신한다.
- 권한(403)·런타임 enabled·폴더 상태(409)는 백엔드에서 강제한다. Prompt 설명으로 끝내지 않는다.
- Agent(논리적 직원)와 Runtime(실행 엔진)은 분리한다. 새 Runtime/Probe/로그인 명령은 인터페이스만 구현하고 등록한다.
- **자주 바뀌는 값은 코드에 넣지 않는다.** 모델/모드 목록은 데이터(화면 편집)로 두고 최종 판단은 CLI 에 위임한다.
