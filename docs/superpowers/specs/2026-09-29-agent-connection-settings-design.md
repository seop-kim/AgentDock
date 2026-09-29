# Agent 연결 설정 재구성 설계 (2026-09-29)

기존 "AI Providers" 화면을 **"Agent 연결 설정"** 으로 재구성한다. Provider 등록과 Connection 관리가 한 화면에 붙어 있어 시인성이 나쁘고, 연결이 끊겨도 Agent 가 계속 실행되며, 로그인 실패 시 사용자가 알아서 터미널을 열어야 하는 문제를 해결한다.

## 확정된 결정 (사용자 합의)

| 항목 | 결정 |
| --- | --- |
| Provider | `CLAUDE_CODE`, `CODEX`, `COMMAND_CODE`, `GEMINI` 4종을 시드한다. 이후 추가 등록도 가능하다 |
| Connection | **Provider 당 1개** (계정 1개). Provider 생성 시 자동 생성된다 |
| 화면 | 메인은 Provider 카드(상태 + "연결" 버튼). "+ Provider 추가"는 모달로 분리한다 |
| 로그인 | OS cmd 창 대신 **웹 화면의 로그인 패널**에서 백엔드가 CLI 로그인 프로세스를 실행하고 출력을 스트리밍한다 (PTY 없는 파이프 방식, 방식 "C") |
| 삭제 | Provider/Connection 삭제를 지원한다. 삭제 정책은 아래 참고 |
| 실행 제어 | Provider 의 Connection 이 `CONNECTED` 가 아니면 **새 실행만 차단**한다. 이미 실행 중인 Execution 은 건드리지 않는다 |

## 범위 밖 (YAGNI)

- PTY 기반 진짜 터미널(추후 메뉴형 CLI 로그인이 필요해지면 `LoginProcess` 구현체만 교체한다)
- Codex / Gemini / Command Code 의 로그인 명령 (CLI 가 이 머신에 없어 플래그를 추측하지 않는다. "로그인 미지원"으로 안내한다)
- 실행 중 Execution 자동 취소, 연결 상태 주기적 자동 재확인
- `capabilities` 기반 모델/모드, OS Credential Store

## 1. 데이터 / 마이그레이션

Flyway `V4__seed_providers.sql`(기존 파일은 수정하지 않는다).

- 4종 Provider 를 `INSERT ... ON CONFLICT (key) DO NOTHING` 으로 시드한다. 표시 이름: Claude Code / Codex / Command Code / Gemini.
- Connection 이 없는 Provider 마다 Connection 을 1개 만든다(`WHERE NOT EXISTS`). 초기 status 는 `DISCONNECTED` 다.
- `ai_connection.provider_id` 에 UNIQUE 제약은 걸지 않는다. 이미 화면에서 여러 개 만든 데이터가 있을 수 있어 마이그레이션이 실패할 수 있기 때문이다. "Provider 당 1개"는 서비스 계층에서 보장한다(409).
- 스키마 컬럼 변경은 없다. `agent.connection_id` 컬럼은 유지한다.

## 2. 백엔드 (provider 패키지)

### Provider / Connection API

| API | 동작 |
| --- | --- |
| `GET /ai-providers` | 기존과 동일 |
| `POST /ai-providers` | Provider 생성 + Connection 자동 생성. 같은 key 가 이미 있으면 409 |
| `DELETE /ai-providers/{id}` | 참조하는 Agent 가 있으면 **409**(메시지에 Agent 이름 목록). 없으면 Connection 과 함께 삭제 |
| `POST /ai-providers/{id}/connection` | Connection 재생성("연결 추가"). 이미 있으면 409 |
| `DELETE /ai-connections/{id}` | Connection 삭제. 참조하는 Agent 의 `connection_id` 를 NULL 로 만든다 |
| `POST /ai-connections/{id}/check` | 기존과 동일 |

기존 `POST /ai-providers/connections` 는 삭제하지 않고 남긴다(화면에서만 쓰지 않는다).

삭제 정책 요약:

- **Provider 삭제 차단 이유**: Agent 는 Provider 없이 존재할 수 없고(`agent.provider_id NOT NULL`), 연쇄 삭제는 Agent → Execution/로그/Task 할당까지 지워 이력이 사라진다.
- **Connection 삭제 허용 이유**: Connection 은 상태 기록이며 실행은 CLI 세션을 쓴다. 삭제하면 그 Provider 의 Agent 는 아래 실행 가드에 의해 실행이 막힌다.

### 실행 가드 (Agent 실행 = 연결 상태에 종속)

`ExecutionService.create(...)` 에서 권한 검사(`TERMINAL_EXECUTE`) 직후, Provider 의 Connection 상태를 확인한다.

- 판단 기준은 **Agent 의 `connection_id` 가 아니라 Agent 의 Provider 에 속한 Connection** 이다(Provider 당 1개이므로).
- Connection 이 없거나 status 가 `CONNECTED` 가 아니면(`DISCONNECTED`/`ERROR`) Runtime 을 호출하지 않고 **409** (`ConflictException`, 메시지: "Provider connection is not CONNECTED: ...") 로 거부한다.
- 구현은 `AiConnectionService.requireConnected(Long providerId)` 로 두고 `ExecutionService` 가 호출한다.
- `POST /executions` 와 `POST /tasks/{id}/run` 이 모두 `ExecutionService.create` 를 지나므로 우회할 수 없다.
- 이미 실행 중인 Execution 은 취소하지 않는다.
- status 는 마지막 확인 시점의 스냅샷이다. 시드 직후에는 `DISCONNECTED` 이므로 사용자가 "연결"을 한 번 눌러야 Agent 가 실행된다.

### 로그인 세션

Provider CLI 의 로그인 프로세스를 실행하고 출력을 웹으로 스트리밍한다.

구성 요소(모두 `provider` 패키지):

- `AiLoginCommand` — Provider 별 로그인 명령 (probe 와 같은 패턴). `providerKey()` 와 `command()` 를 가진다. `ClaudeCodeLoginCommand` 만 구현한다: `[CLAUDE_CODE_BIN(기본 claude), "auth", "login"]` (`claude auth login --help` 로 존재 확인함). 구현체가 없는 Provider 는 "이 Provider 는 로그인 창을 아직 지원하지 않습니다" 를 반환한다. 명령은 **서버 고정값이며 사용자 입력은 받지 않는다**.
- `LoginCommandRegistry` — `List<AiLoginCommand>` 주입으로 key 별 색인 (`ProbeRegistry` 와 같은 방식).
- `LoginProcess` (인터페이스) — `start(command)`, `onOutput(listener)`, `write(text)`, `close()`. 구현체 `PipeLoginProcess`(`ProcessBuilder`, stderr 병합). 추후 `PtyLoginProcess` 로 교체할 수 있게 경계를 둔다.
- `LoginSessionService` — 세션 관리(`ConcurrentHashMap`). 출력은 **버퍼에 쌓아 두고**, 스트림 구독 시 지금까지의 출력을 재생한다(구독이 시작 요청보다 늦어도 URL 출력을 놓치지 않기 위함). 프로세스 종료 시 exit code 이벤트를 보낸다. 유휴 5분이 지나면 강제 종료한다. Connection 당 활성 세션은 1개이며 이미 있으면 그 세션을 반환한다.

API:

| API | 동작 |
| --- | --- |
| `POST /ai-connections/{id}/login` | 로그인 세션 시작 → `{ sessionId }`. 명령 미지원이면 400 |
| `GET /ai-connections/login-sessions/{sessionId}/stream` | SSE. 이벤트: `output`(텍스트 조각), `exit`(exitCode). 구독 시 버퍼 재생 |
| `POST /ai-connections/login-sessions/{sessionId}/input` | 프로세스 stdin 으로 텍스트 한 줄 전달(인증 코드 붙여넣기용) |
| `DELETE /ai-connections/login-sessions/{sessionId}` | 세션 종료 |

브라우저 인증 창은 백엔드가 도는 PC 에서 열린다. 이 앱은 로컬 전용 도구이므로 문제 없다. 웹 패널에는 출력에서 추출한 인증 URL 링크도 함께 보여 주어 브라우저가 자동으로 열리지 않을 때 대비한다.

## 3. 프론트엔드 (`apps/frontend`)

- 화면 제목/메뉴는 이미 "Agent 연결 설정" 이다. 라우트 `/providers` 와 파일명은 유지한다.
- **Provider 카드**: 이름, key, 상태 배지, 마지막 확인 시각, 오류, "연결" 버튼(= check), Provider 삭제 버튼. Connection 이 없으면 "연결 추가" 버튼(= `POST /ai-providers/{id}/connection`)과 "Connection 없음" 표시.
- **"+ Provider 추가" 모달**: 화면 우측 상단 버튼으로 연다. key 드롭다운에는 **아직 등록되지 않은 key 만** 나온다. 표시 이름 입력. 기존 Provider/Connection 추가 폼은 화면에서 제거한다.
- **로그인 패널**(카드 하위 컴포넌트): "연결" 확인이 `ERROR` 로 끝나면 카드에 "로그인" 버튼이 나타난다. 누르면 세션을 시작하고 SSE 로 출력을 `<pre>` 에 표시한다(ANSI 이스케이프 제거, URL 링크화). 입력창 + 전송 버튼, "닫기"(세션 종료) 버튼. 프로세스가 `exit 0` 으로 끝나면 자동으로 check 를 호출해 상태를 갱신한다. 로그인 명령이 미지원인 Provider 는 그 사유를 안내한다.
- **삭제 확인창**: Connection 삭제 시 "이 Provider 의 Agent N개는 연결이 복구되기 전까지 실행되지 않습니다". Provider 삭제가 409 면 서버 메시지(참조 Agent 이름)를 그대로 보여 준다.
- 서버 오류 메시지는 기존처럼 화면에 표시한다.

## 4. 에러 처리

| 상황 | 결과 |
| --- | --- |
| 중복 key 로 Provider 생성 | 409 |
| 참조 Agent 가 있는 Provider 삭제 | 409 + Agent 이름 목록 |
| Connection 이 이미 있는데 재생성 | 409 |
| Connection 없음/미연결 상태의 Agent 실행 | 409 (Runtime 호출 안 함) |
| 로그인 명령 미지원 Provider | 400 + 안내 메시지 |
| CLI 바이너리 없음 | 세션 시작 실패를 `exit` 이벤트/메시지로 전달 |
| 로그인 세션 유휴 5분 | 프로세스 강제 종료 |

## 5. 테스트 / 검증

기존 테스트는 컨텍스트 로딩 1건뿐이므로, 새 로직은 JUnit 테스트를 추가한다.

- 실행 가드: Connection 없음/`DISCONNECTED`/`ERROR` 는 거부, `CONNECTED` 는 통과.
- Provider 삭제: 참조 Agent 있으면 409, 없으면 Connection 과 함께 삭제.
- Connection 삭제: 참조 Agent 의 `connection_id` 가 NULL 이 됨.
- 로그인 세션: 출력 버퍼 재생, 종료 이벤트, 유휴 종료(가짜 `LoginProcess` 로 검증).
- 수동 검증(백엔드 8081 / 프론트 3031): 시드 확인, 카드 화면, 모달, 로그인 패널이 `claude auth login` 출력을 표시하는지, 삭제 정책, 연결 끊김 상태에서 Agent 실행이 409 인지.
- `bootRun` 은 성공처럼 보여도 실패할 수 있으므로 반드시 기동 로그(`Started AgentDockApplication`)나 HTTP 응답으로 확인한다.

## 6. 문서

구조가 바뀌므로 `agents/CONVENTIONS.md`(Provider 시드, Connection 1:1, 실행 가드, 로그인 세션)와 `agents/HANDOVER.md` 를 함께 갱신한다.
