# Agent 모델/모드 — Provider capabilities 기반 선택과 CLI 전달 (구현 완료)

브랜치 `feat/model-mode`. 2026-09-29 구현·검증 완료.

## 목표

1. Provider별 지원 모델/모드를 **데이터**로 관리하고 화면에서 편집한다(코드 하드코딩 금지 — 목록이 자주 바뀌는 문제 대응).
2. Agent 의 `model`/`mode` 를 **실제 CLI 플래그로 전달**한다. 구현 전에는 `mode` 가 저장만 되고 어디서도 쓰이지 않았다.
3. 목록에 없는 값을 입력해도 **막지 않는다**(최종 판단은 CLI). 대신 화면에서 "목록에 없는 값" 힌트만 준다.

## 배경 (실측 근거)

- `claude --help` 로 확인한 사실:
  - `--model <model>` — alias(`fable`, `opus`, `sonnet`) 또는 전체 이름(`claude-fable-5`)
  - `--permission-mode <mode>` — choices: `acceptEdits`, `auto`, `bypassPermissions`, `manual`, `dontAsk`, `plan`
  - `--fallback-model <model>` 도 존재
- **모델 목록을 출력하는 명령은 없다.** (`claude models` 는 서브커맨드가 아니라 프롬프트로 처리됨) → 자동 수집 불가, 수동 편집이 현실적인 유일한 방법.
- 구현 전 코드 상태:
  - `ClaudeCodeRuntime` 은 `--model` 만 args 에 추가하고 `mode` 는 읽지 않았다.
  - `AgentExecutionRequest` 는 model/mode 를 이미 싣고 있으므로 런타임만 고치면 됐다.
  - `AiProvider.capabilities`(JSONB)는 생성 시 1회 저장만 되고 **읽는 코드가 전혀 없었고**, 갱신 API 도 없었다.
  - 프론트 `AiProvider` 타입에 `capabilities` 가 없고, Agents 폼에 `mode` 필드가 없었다.

## 설계 결정

| 항목 | 결정 |
| --- | --- |
| 목록의 진실 | `ai_provider.capabilities`(JSONB). **화면에서 편집**, API 로 갱신. 코드에 목록을 두지 않는다 |
| 값 형식 | `{"models": [...], "modes": [...], "notes": "..."}`. **모르는 키는 보존**한다 |
| 검증 | **관대(permissive)** — `Agent.model`/`Agent.mode` 는 자유 문자열 유지. 목록은 제안(datalist)일 뿐 |
| Mode 의미 | Provider CLI 의 **실행/권한 모드**. Claude Code = `--permission-mode` 의 6종 |
| 다른 Provider | 목록을 비워 둔다(추측 금지). 필요하면 화면에서 채운다 |
| 전달 | `ClaudeCodeRuntime` 이 `--model`(기존) + `--permission-mode`(신규) 로 전달 |

## 1. 마이그레이션 `V5__provider_capabilities_seed.sql` (완료)

`capabilities` 가 빈 객체(`'{}'`)인 행에만 시드한다(사용자가 이미 편집한 값은 건드리지 않는다).

- `CLAUDE_CODE`: `models = ["opus","sonnet","fable"]`, `modes = ["acceptEdits","auto","bypassPermissions","manual","dontAsk","plan"]` + 메모
- `CODEX`/`COMMAND_CODE`/`GEMINI`: 빈 목록 + "CLI 미설치로 미확인" 메모

스키마 변경(컬럼 추가)은 없다.

## 2. 백엔드 (완료)

- `ProviderCapabilities`(record: models/modes/notes)
- `AiProviderService.updateCapabilities(id, request)` — 논리 삭제된 Provider 404, 목록 정규화(trim·빈값·중복 제거), notes 공백이면 키 제거, **다른 capability 키 보존**
- `PUT /ai-providers/{id}/capabilities` (200)
- `ClaudeCodeRuntime.buildArgs(request)` 로 인자 조립을 추출(단위 테스트 가능). `model` → `--model`, `mode` → `--permission-mode`, 값이 없으면 플래그 생략

## 3. 프론트엔드 (완료)

- `api.ts`: `ProviderCapabilities` 타입, `AiProvider.capabilities`, `updateProviderCapabilities`
- `ProviderCard`: 모델/모드 요약 줄 + "모델/모드 편집" 인라인 패널(줄바꿈/콤마 구분, notes)
- `Agents`: Provider 선택 시 그 Provider 의 capabilities 로 datalist 를 만들고 자유 입력 허용, `mode` 필드 신규, 목록에 없는 값 경고, Agent 목록에 Model/Mode 열
- `RunAgentModal`: 실행에 사용될 model/mode 읽기 전용 표시

## 4. 테스트 / 검증 (완료)

- JUnit: capabilities 정규화/보존/404 (`AiProviderServiceTest`), `buildArgs` 플래그 조립 (`ClaudeCodeRuntimeTest`)
- E2E: `PUT` 으로 목록 갱신 → 조회 반영, 실제 `claude` 로 `model=opus, mode=plan` 실행 성공(exit 0), `mode=definitely-not-a-mode` 실행 실패(exit 1, **플래그가 CLI 까지 전달된 증거**)
- UI: 카드 요약/편집 패널, Agents 지원 목록 힌트와 목록에 없는 값 경고

## 5. 문서 (완료)

- `agents/CONVENTIONS.md`: "모델과 모드 (`capabilities`)" 절 추가, provider 모듈/DB 스키마/컨벤션 갱신
- `agents/HANDOVER.md`: 상태와 다음 작업 갱신

## 범위 밖 (이번에 하지 않음)

- CLI 출력에서 모델 목록 자동 수집(그런 명령이 없음)
- Codex/Gemini/Command Code 의 목록·모드 확정(CLI 미설치, 추측 금지)
- `Agent.profile`(JSONB) 활용, `model`/`mode` 의 엄격 검증, Phase 3(Workflow)
