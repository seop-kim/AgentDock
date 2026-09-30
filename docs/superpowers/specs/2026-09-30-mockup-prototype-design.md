# 목업 프로토타입 설계

## 배경과 목적

구현을 잠시 멈추고, 앱 전체 목업을 먼저 만든다. 이 목업이 이후 개발의 기준이 되고, 목업을 만들면서 기획서/설계서를 새로 작성한다. 이 스펙은 **1차 범위(기존 프로젝트의 화면을 목업으로 재현)** 만 다룬다. 기획 내용(Stage 2~4 신규 개념 등)은 사용자가 이후에 제공한다.

## 확정된 결정

| 항목 | 결정 |
| --- | --- |
| 산출물 | 클릭 가능한 정적 프로토타입 |
| 구현 방식 | Vite 5 + React 18 + React Router 6 + TypeScript + CSS Modules (기존 프론트와 동일 스택) |
| 데이터 | 백엔드/DB 없음. 테스트 데이터를 메모리(React 상태)에만 두고, 새로고침하면 초기화 |
| 동작 | 버튼과 이벤트는 실제로 동작(생성/삭제/토글/할당/실행 등). 결과는 화면에 보이는 것만 |
| 범위(1차) | 기존 라우트와 모달을 그대로 재현. 디자인 토큰도 현재 값(`globals.css`) 그대로 |

## 폴더 배치

```
apps/
  backend/     기존
  frontend/    기존
  mockup/      신규. 목업 앱 (frontend 와 같은 위치)
docs/
  planning/    신규. 기획서/설계서 보관
  superpowers/ 기존. 스펙/계획
```

- `apps/mockup` 은 독립 앱이다. `apps/frontend` 와 `apps/backend` 는 수정하지 않는다.
- 루트 `package.json` 의 `workspaces` 에 `apps/mockup` 을 추가하고, 스크립트 `dev:mockup` 을 둔다.
- 목업 dev 서버 포트는 **3040** 이다(사용자 3030, 에이전트 검증 3031 과 겹치지 않게).
- `docs/planning/` 의 이름과 위치는 가정이며, 사용자가 다르게 지정하면 옮긴다.

## 화면 범위

기존 `apps/frontend/src/App.tsx` 라우트를 그대로 옮긴다.

| 경로 | 화면 |
| --- | --- |
| `/` | Dashboard |
| `/projects` | Projects (워크스페이스 할당 포함) |
| `/groups` | Groups (리더/멤버) |
| `/tasks` | Tasks (실행 포함) |
| `/agents` | Agents (생성, 런타임 재할당) |
| `/workspaces` | Workspaces (폴더 선택 `WorkspacePicker`, 폴더별 런타임 상태) |
| `/providers` | 에이전트 설정 (런타임 ON/OFF, 모델/모드/설치 계획 편집, 로그인/설치) |
| `/executions/:id` | ExecutionDetail (로그 스트림) |

모달/패널: RunAgentModal, CliPanel, CommandPanel, AddProviderModal, WorkspacePicker, AgentProviderAssign.

구현 시작 전에 위 각 화면의 실제 소스를 읽어 필드/버튼/문구를 그대로 재현한다.

## 데이터와 동작

- `src/data/seed.ts`: 런타임(4종), 워크스페이스, 프로젝트, 에이전트, 그룹, Task, Execution 의 테스트 데이터. 현재 도메인 구조(`agents/CONVENTIONS.md`)를 따른다.
- `src/store/`: React 컨텍스트 + reducer 로 메모리 상태를 보관한다. 화면은 `lib/api.ts` 대신 이 스토어를 읽고 쓴다.
- 가짜 비동기: Task 실행, CLI 확인(probe), 로그인/설치는 타이머로 출력 라인을 순서대로 흘려 SSE 처럼 보이게 한다. 성공/실패 시나리오를 시드로 몇 개 준비한다.
- 없는 백엔드 규칙(권한 403, 연결 가드 409)은 화면에 보이는 수준으로만 흉내 낸다(사용 불가 사유 표시 등).
- 어떤 데이터도 저장하지 않는다(`localStorage` 포함 사용하지 않음).

## 구조

```
apps/mockup/
  package.json, tsconfig.json, vite.config.ts, index.html
  src/
    main.tsx, App.tsx, layout.module.css, globals.css
    data/seed.ts
    store/            상태, reducer, 가짜 스트리밍 훅
    pages/            화면별 파일 (frontend 구조를 따름)
    components/       모달/패널 공용 컴포넌트
```

한 파일은 한 화면 또는 한 책임만 갖는다.

## 검증

- `npm run build --workspace=apps/mockup` (tsc + vite build) 통과.
- dev 서버(3040)에서 각 라우트가 열리고, 핵심 버튼(생성/삭제/토글/실행/CLI 확인)이 동작하는지 브라우저로 확인한다.

## 범위 밖

- Stage 2~4 신규 개념(Workflow, Shared Context, Message, Artifact, Review, Decision)
- 실제 API 연동, 영속 저장
- 기획서/설계서 본문 작성 (폴더만 만든다. 본문은 기획을 받은 뒤 작성)
- 디자인 재설계 (1차는 현재 디자인 재현)

## 커밋과 브랜치

작업 브랜치는 `mockup` 이다. 기능 브랜치 컨벤션(`feat:`, `docs:`)으로 소단위 커밋하고, `Co-Authored-By:` 트레일러는 붙이지 않는다. 구조/컨벤션이 바뀌면 `agents/CONVENTIONS.md` 에 `apps/mockup`, `docs/planning` 을 함께 반영한다.
