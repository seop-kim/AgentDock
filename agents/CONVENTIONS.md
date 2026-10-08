# AgentDock — 공용 작업 기준 문서

이 저장소에는 Claude Code, Codex, Command Code, Gemini CLI 등 여러 AI 코딩 에이전트가 함께 작업한다. 이 파일이 모든 에이전트가 공유하는 단일 기준 문서이며, 루트의 진입점 파일들은 각 툴이 자동으로 찾는 포인터일 뿐 실제 내용은 여기 있다. **어떤 에이전트든 작업을 시작하기 전에 이 파일을 먼저 읽고, 구조/컨벤션을 바꾸면 이 파일도 함께 갱신한다.**

## 진입점 파일 (루트)

각 툴이 시작 시 자동으로 읽는 파일명이 다르므로, 루트에 동일한 내용(이 파일로의 포인터)을 여러 이름으로 둔다.

| 파일 | 대상 툴 |
| --- | --- |
| `CLAUDE.md` | Claude Code |
| `AGENTS.md` | Codex, 그리고 `AGENTS.md` 규격을 지원하는 대부분의 에이전트 |
| `GEMINI.md` | Gemini CLI |

Command Code처럼 위 세 파일 중 아무것도 자동으로 읽지 않는 툴을 쓸 경우, 그 툴의 system prompt/설정에 "작업 전에 `agents/CONVENTIONS.md`를 읽어라"를 직접 지정한다.

진행 상태와 다음 작업은 [`agents/HANDOVER.md`](./HANDOVER.md) 를 본다(현재는 **1차 구현 — 위임 코어와 프로젝트 상세**). 이 문서는 규칙·구조의 기준이고, HANDOVER 는 "지금 어디까지 됐고 다음에 뭘 할지"의 스냅샷이다. 목업 이전의 백엔드/프론트 구현 단계 인수인계(일시 중단)는 [`agents/HANDOVER-implementation.md`](./HANDOVER-implementation.md) 에 보관한다.

## 기술 스택

- Backend: **Java 25 + Spring Boot 4.1.1** + Gradle(Groovy DSL) + Spring Data JPA(Hibernate) + QueryDSL + Flyway + Lombok + PostgreSQL (`apps/backend`)
- Frontend: **Vite 5 + React 18 + React Router 6** + TypeScript + CSS Modules (`apps/frontend`, 순수 SPA)
- Local Runtime 실행: `ProcessBuilder` (shell 미경유), 브라우저가 CLI를 직접 실행하지 않음
- 실시간 로그/출력: SSE (Server-Sent Events, `SseEmitter`) — 실행 로그는 `GET /executions/{id}/stream`, **데이터 변경 알림은 전역 `GET /events/stream`**

## 핵심 설계 원칙 (스펙 33장)

1. Agent(논리적 직원) != AI Runtime(실행 엔진). `AgentRuntime` 인터페이스로 분리하고 런타임 설정에 따라 구현체(ClaudeCodeRuntime, ...)를 선택한다.
2. Task/Execution 중심 설계. Agent 자체보다 실행 단위(Execution)와 결과(Log)를 추적한다.
3. 권한은 Prompt 설명만으로 강제하지 않는다. Backend(ExecutionService)에서 실제로 차단한다.
4. Agent 간 통신은 전체 대화 공유가 아니라 Context/Message/Artifact/Decision 단위로 제한한다.
5. 모든 실행은 DB에 기록해 추적 가능해야 한다 (execution, execution_log).

## 현재 도메인 구조 (Stage 1 재편 완료)

화면·개념은 다음과 같다. 상세 계획/검증 기록은 `docs/superpowers/plans/2026-09-29-workspace-runtime-restructure.md`.

| 개념 | 내용 |
| --- | --- |
| **AI 런타임** (`ai_provider`) | claude / codex / command code / gemini. **on/off**(`enabled`)만 화면에서 관리한다. 로그인은 런타임 단위(웹 로그인 패널), 모델/모드 목록은 `capabilities` 데이터 |
| **워크스페이스** (`workspace`) | 로컬 폴더 경로. **폴더별 런타임 상태**(`workspace_runtime_status`)를 그 폴더를 작업 디렉터리로 CLI 를 실제 실행해 확인한다(probe cwd) |
| **프로젝트** (`project`) | 워크스페이스를 **N개** 할당(`project_workspace`, 기본 1개). 에이전트를 프로젝트 안에서 만든다 |
| **에이전트** (`agent`) | **프로젝트 소속**(`project_id NOT NULL`). 런타임 + Role + Permission + **페르소나**(`persona`, 그 에이전트만의 성격/일하는 방식 프롬프트) + model/mode |
| **그룹** (`agent_group`) | 프로젝트 안에서 에이전트를 묶고 리더를 지정 |
| **Task / Execution** | 프로젝트 소속. 실행 디렉터리는 프로젝트의 **기본 워크스페이스**(git 저장소면 그 저장소의 **worktree**, 아래 "워크트리 격리") |
| **Agents 메뉴** | 전 프로젝트 에이전트 목록(읽기용) + 이 화면에서도 생성 가능(Stage 2 에서 프로젝트 상세로 이동 예정) |
| **Tasks 메뉴** | 진행/종료 작업 목록 |

**삭제된 개념**: Provider 단위 Connection(`ai_connection`)과 "Agent 연결 설정" 화면. 연결 상태는 이제 워크스페이스(폴더) 단위다.

Workflow/WorkflowStep, Shared Context, Message, Artifact, Review, Decision 은 이후 Stage(2~4)에서 확장한다.

## 화면 설계 기준 (목업에서 확정 → `apps/frontend` 에 이식 중)

**프론트엔드 폴더 규칙(기능 우선)**: `apps/frontend/src` 는 `components/`(공용 컴포넌트) · `lib/`(api·storage·표시 유틸) · `store/`(전역 컨텍스트) · `styles/`(토큰·공용 CSS 모듈) · `features/<도메인>/` 으로 나눈다. 화면과 그 화면 전용 컴포넌트·CSS 는 **해당 도메인 폴더 안에** 둔다(`features/project/ProjectDetail.tsx`, `features/execution/TerminalWindow.tsx`). 새 화면은 만들어질 때 그 도메인 폴더에 넣고, 다른 도메인 것을 가져다 쓸 때만 `../<도메인>/X` 로 참조한다.

`apps/mockup`(독립 앱, 포트 3040)에서 화면·UX 를 먼저 확정했고, 그 **디자인 시스템과 화면 구조를 `apps/frontend` 로 옮기는 중**이다. 목업은 기준 화면으로 계속 남겨 둔다(수정하지 않는다 — 값을 비교할 때만 본다). 아래 규칙은 이제 **`apps/frontend` 에도 그대로 적용된다**: `src/styles/tokens.css`, `src/styles/glass.module.css`, `src/styles/shared.module.css`, `src/styles/modal.module.css`, `src/styles/execution.module.css`, `src/styles/terminal.module.css`, `src/components/Sidebar.tsx`, `src/store/ThemeContext.tsx`, `src/lib/storage.ts`(테마 키 `agentdock-theme`)가 이식됐고, 프로젝트 상세는 `src/features/project/ProjectDetail.tsx`(+ `src/features/execution/ExecutionTreeModal.tsx`, `.../TerminalWindow.tsx`)다. 목업에만 있는 화면(구성도 캔버스, 첨부, 현실성 점검, 설정 안쪽 메뉴)은 다음 단계다.

목업 단계는 끝났다(`mockup` 브랜치 → `dev` 로 머지됨, `[Feat] 목업: … (#6)`). 목업을 만들면서 확정한 화면·규칙이 이후 개발의 기준이고, 기획서/설계서는 `docs/planning/` 에 쓴다(설계 근거: `docs/superpowers/specs/2026-09-30-mockup-prototype-design.md`).

- 독립 Vite + React + TypeScript 앱(포트 **3040**, `npm run dev:mockup`). `apps/frontend`/`apps/backend` 는 건드리지 않는다.
- 백엔드/DB 없음. 테스트 데이터는 `src/store/seed.ts`, 상태는 `src/store/MockStore.tsx`(메모리, 새로고침하면 초기화). `localStorage` 는 **UI 설정만** 저장한다: 테마 선택, 사이드바 펼침 여부(키는 `src/lib/storage.ts` 의 `STORAGE_KEYS`, 접근은 그 파일의 `readStorage`/`writeStorage` 로만 한다. 테마는 첫 화면 깜빡임 방지용 초기 적용 스크립트가 `index.html` 에 있으며 키를 같게 유지한다). 그 밖의 데이터는 저장하지 않는다.
- **CSS 는 전부 파일로 분리**한다(인라인 `style` 금지). 색은 `src/styles/tokens.css` 의 토큰(`var(--color-...)`)만 쓴다. 화면 고유 스타일은 `*.module.css`, 공용 조각은 `styles/shared.module.css`. **셀렉트는 `appearance: none` + 토큰 화살표**(`--select-arrow`, 라이트/다크 3곳에 정의)로 모드에 맞춘다(`globals.css`).
- **글래스 스타일**: 위에 떠 있는 패널은 반투명 + 블러(`styles/glass.module.css` 의 `glass`/`glassStrong` 를 `composes` 로 가져다 쓴다). 라이트는 배경에 색이 번진 그라데이션(`--app-bg`)을 깔고, 다크는 무채색으로 배경을 너무 어둡지 않게(#161616) 두되 **떠 있는 것들(패널, 채팅, 그룹 상자, 노드)은 배경보다 확실히 밝은 회색**으로 구분한다. 왼쪽 사이드바는 글래스가 아니라 평평한 기본 디자인(`--color-sidebar`)이다. **글래스(backdrop-filter) 요소에는 `overflow` 를 두지 않는다** — 크롬이 blur 를 모서리 밖으로 **네모나게** 그려 둥근 모서리 바깥에 색이 새어 보인다. 자르기가 필요하면 안쪽 요소(`.cards` 처럼 블러가 없는 것)에서 한다.
- **다크모드 지원**: `tokens.css` 가 라이트/다크 값을 정의한다. `html[data-theme]` 명시값 > 시스템 설정 > 라이트. 새 색이 필요하면 토큰을 라이트·다크 양쪽(그리고 `prefers-color-scheme` 블록)에 함께 추가한다.
- **메뉴는 왼쪽 사이드바**(`components/Sidebar.tsx`)에 모은다. 기본은 아이콘만 보이고, 위의 메뉴 버튼을 누르면 이름까지 펼쳐지며 다시 누르면 접힌다. 메뉴를 추가하려면 `Sidebar.tsx` 의 `MENUS` 에 항목을, `components/icons.tsx` 에 아이콘을 추가한다.
- **설정 화면**(`/settings`)은 사이드바에 "설정" 아이콘 하나만 두고, 화면 안쪽 왼쪽 메뉴(`pages/Settings/Settings.tsx`)로 항목을 구분한다(탭·사이드바 하위 메뉴 아님): **에이전트 연결 설정**(`/settings/agents`, 런타임 카드), **테마**(`/settings/theme`, 시스템/라이트/다크. `store/ThemeContext.tsx`). 항목을 추가하려면 `Settings.tsx` 의 `SECTIONS` 와 `App.tsx` 의 중첩 라우트에 추가한다.
- 내용 영역은 사이드바를 뺀 화면 전체 폭을 쓴다(`max-width` 로 가운데 정렬하지 않는다).
- **프로젝트**: 목록(`/projects`)은 **카드 뷰**로 이름·워크스페이스·에이전트 수·총 Task 수를 보여 주고 카드를 누르면 상세(`/projects/:id`)로 간다. 상세는 **구성도 캔버스**(`GroupCanvas.tsx`)가 사이드바를 뺀 화면 전체를 채우고, 그 위에 헤더 카드(이름 줄 오른쪽 끝에 **설정** 아이콘 버튼, 기본 워크스페이스, 에이전트/그룹/Task 수), 왼쪽 **에이전트 패널**(기본은 열림), 가 떠 있다. **왼쪽 아이콘 사이드바는 모든 화면에서 항상 있다**(프로젝트 상세도 마찬가지). **워크스페이스 할당과 프로젝트 이름 변경/삭제는 설정 창**(`ProjectSettingsModal.tsx`)에서만 한다.
- **왼쪽 패널**: 헤더 카드 아래에 **에이전트 카드 목록**(`AgentList.tsx`)과 그 밑의 **그룹 카드 목록**(`GroupList.tsx`)이 있다. 그룹은 여기서 만들고 지운다(새 그룹은 이름을 받는 **창**, `GroupFormModal.tsx`). 에이전트/그룹 추가 버튼은 색·글자 없이 **+ 아이콘**만 쓴다(`shared.addButton`). **각 상자 안 상단 중앙에는 얇은 화살표 손잡이**(⌃ 펼침 / ⌄ 접힘, 보이는 것은 화살표뿐이고 누를 수 있는 넓이는 `padding` 으로 확보한다)가 있고, **머리말 줄을 눌러도 같은 동작**을 한다. 접고 펼 때는 **높이가 부드럽게 바뀐다**(패널 `flex-grow` 전환 + 몸통 `grid-template-rows` 1fr↔0fr, 0.22s. 접었을 때 높이는 손잡이+머리말 = `min-height: 69px`). 상자 안쪽 여백은 **위 3px, 좌우·아래 10px**(화살표 손잡이를 상단에 붙이려고 위만 좁게 둔다). 머리말 오른쪽의 `+` 는 `stopPropagation` 으로 창만 연다. 둘 다 접으면 구성도가 화면 전체를 쓴다. 에이전트 카드를 그룹 카드(또는 구성도의 그룹 상자)로 끌어 놓으면 멤버가 된다(`lib/useGroupDrop.ts` 가 공통 처리).
- **에이전트 카드**: 이름과 역할만 보이는 작은 카드. 오른쪽 **"⋮" 메뉴**(`AgentCardMenu.tsx`)에서 **상세 설정**(수정, `AgentFormModal.tsx` 를 생성과 같이 쓴다)과 **삭제**(모든 그룹에서도 빠지고 리더면 다음 멤버가 이어받는다)를 한다. **마우스를 올리면** 이름·역할·런타임·소속 그룹·**연결된 에이전트**(같은 그룹의 다른 멤버)·**프롬프트(persona)** 를 `AgentHoverCard.tsx` 정보 창으로 보여 준다. **누르면 선택**되어 카드가 강조되고 구성도가 그 에이전트 노드로 이동하며(`focusSeq`), 구성도에서도 그 노드가 "선택됨" 이름표와 고리로 강조된다. **선택된 에이전트를 다시 누르면 선택이 풀린다**(카드/노드 모두). 선택은 채팅 대상도 겸한다. 메뉴/정보 창과 **생성·수정 창**(`AgentFormModal.tsx`, `GroupFormModal.tsx`)은 패널의 블러·스크롤에 잘리지 않도록 `createPortal` 로 body 에 그린다.
- **에이전트 배치와 상태**: 에이전트는 구성도에 **놓이거나(placed) 빠질** 수 있다. 구성도 노드의 **`⋮` 메뉴**(`AgentNodeMenu.tsx`: 리더로 지정 / 그룹에서 제거 / 구성도에서 빼기)와 카드 **`⋮` 메뉴**(`AgentCardMenu.tsx`: 상세 설정 / 구성도에서 빼기·놓기 / 삭제)에서 한다. **빼면 그룹에서도 빠지고 에이전트 목록에만** 남는다(다시 놓으면 그룹 없이 노드만). 노드에는 버튼을 늘어놓지 않고 **`⋮` 하나만** 두며 리더는 ★ 로만 표시한다. 카드 왼쪽에 **그립(⋮⋮)을 두지 않는다**(카드 전체가 드래그 대상). 카드 오른쪽 **상태 배지**: **미배치**(구성도에 없음) / **작업 중**(맡은 Task 가 RUNNING) / **작업 대기중**(PENDING 있음) / **작업 없음**(그 외). 판정은 `lib/agentStatus.ts` 의 순수 함수이고, Task 는 `agentId` 로 담당(그룹에 보내면 리더)을 갖는다. `⋮` 팝업 메뉴 스타일은 `styles/menu.module.css` 공용이다.
- **마스터 에이전트와 프롬프트 계층**: 프로젝트마다 **마스터 에이전트**를 하나 둔다(`project.masterAgentId`, 최상위 리더). 마스터는 **그룹에 속하지 않는다**(지정하면 모든 그룹에서 빠지고, 그룹에 끌어 놓으면 막는다). 프롬프트는 **마스터(프로젝트) → 그룹 → 에이전트** 순서로 겹쳐 적용된다: `project.masterPrompt`(프로젝트 설정 창), `group.prompt`(그룹 카드 ⋮ → 프롬프트 편집), `agent.persona`(에이전트 창). 마스터는 목록 카드·구성도 노드에 **마스터** 표시가 붙고, **구성도에서 뺄 수 없고 삭제도 안 된다**(카드 `⋮` 에서 두 항목을 숨기고 노드에는 `⋮` 자체를 두지 않는다). 마스터 변경은 프로젝트 설정 창에서 한다.
- **채팅(명령) 창**(`ChatPanel.tsx`): 구성도 **오른쪽에 세로로 붙는 패널**(너비 360, 위/아래 여백 16). **왼쪽 테두리 안쪽에 얇은 화살표**가 있다(세로 가운데에 `‹` 하나만 보이고, **클릭 범위는 왼쪽 세로 전체** — 둥근 모서리를 피해 위아래 16px 만 비운다. 누르면 좌우로 넓어지고 `›` 로 바뀐다. 화살표 자리로 패널 왼쪽 여백은 14px). 넓어지면 일시적으로 왼쪽 패널 옆부터 창 오른쪽까지 차지한다(같은 패널이라 쓰던 글·첨부가 그대로 남는다. 팝업으로 따로 그리지 않는다 — 상태가 갈라진다). 머리말 **왼쪽**의 접기 버튼을 누르면 머리말만 남고(기록 수만 보인다) 구성도가 그 폭을 되찾으며, 접을 때 크게 보기도 함께 풀린다. **기본 대상은 프로젝트 마스터**이고 그룹/에이전트도 직접 고를 수 있다(리더가 없거나 실행 불가면 시스템 메시지로 사유를 알린다). 왼쪽 에이전트/그룹 카드를 누르면 대상이 선택된다. 명령을 보내면 **실행 트리**가 만들어지고, 응답은 계획이 다 끝난 뒤에 온다. 응답 아래에는 **실행 요약 카드**가 붙는다(`ExecutionTree.tsx` 의 `ExecutionSummaryCard`). **기록 영역은 남는 높이를 다 쓰고 길면 안에서 스크롤**한다(내용이 길어져도 창이 커지지 않는다). **입력줄(📎 + 입력 + 보내기)은 상자들과 같은 형태로 한 칸에 묶는다**(둥근 테두리 + `--color-input-bg`, 여백 위 3·좌우 10, 입력 자체는 테두리·배경 없이 두고 포커스 표시는 `:focus-within` 으로 칸 전체에 준다). 로직은 `MockStore.sendCommand`(타이머로 재생, 메모리에만 저장).
- **에이전트 터미널**(`pages/TerminalWindow.tsx`, `components/TerminalView.tsx`, `lib/terminal.ts`, `lib/windowSync.ts`): 에이전트 카드/노드의 `⋮` → **터미널 보기**를 누르면 그 에이전트의 실행 출력이 **새 창**으로 뜬다(`/terminal/:agentId`, 사이드바·레이아웃 없이 그리는 별도 라우트. 메뉴 클릭 안에서 `window.open` 하므로 팝업 차단에 걸리지 않는다). 목업 상태는 메모리에 있으므로 메인 창이 상태가 바뀔 때마다 `localStorage` + `BroadcastChannel` 로 넘겨주고(`publishSnapshot`), 새 창이 그것을 읽고 받는다(`subscribeSnapshot`/`readSnapshot`). 터미널 창 자신은 넘기지 않는다(시드로 덮어쓰지 않게). 실제 구현에서는 새 창이 그 실행의 로그 스트림(SSE)을 직접 구독하면 되고 이 다리는 필요 없다. 출력은 **모의**이고 화면에 그 사실을 적는다. 진행 중이면 줄이 계속 늘어나고 커서가 깜빡이며, `lib/terminal.ts` 의 `terminalState` 가 live/done/idle 을 정한다(**맡은 Task 가 진행 중이면 그것이 우선** — 마지막 실행이 이미 끝났어도 터미널은 살아 있다). 색은 `styles/terminal.module.css` 와 토큰(`--color-output-*`, `--color-term-ok`, `--color-term-dim`)만 쓴다.
- **파일 첨부**(`AttachFilesModal.tsx`, `lib/attachments.ts`, `styles/attachment.module.css`): 붙이는 길이 둘이다.
  ① 입력줄 왼쪽 **📎 버튼** → **파일 첨부 창**: 프로젝트 **워크스페이스 폴더 안의 파일**을 폴더별로 묶어 고른다(워크스페이스가 둘 이상이면 셀렉트가 뜬다). 실제 제품에서는 `GET /workspaces/browse` 로 진짜 폴더를 읽는다.
  ② **채팅 창에 파일을 끌어다 놓기**: 창 밖의 **아무 파일이나** 붙일 수 있다(폴더는 받지 않는다 — 파일만). 에이전트는 워크스페이스 폴더 밖을 볼 수 없으므로 **프로젝트 안 폴더(`.agentdock/attachments`)로 복사한 것으로 치고**, 그 사본 경로를 첨부로 단다(끌어오는 동안 창에 점선 테두리와 안내가 뜬다). **저장 이름은 `<uuid 앞 8자>-원래이름`** 으로 붙여(`lib/attachments.ts` 의 `storedName`) 같은 이름을 여러 번 붙여도 충돌하지 않게 한다. 화면에는 **첨부 칩 = 원래 이름**, **파일 목록 = 저장 이름**을 보여 준다(`displayName` 이 앞의 id 를 떼어 낸다). 복사된 파일은 **워크스페이스 파일 목록에 더해져** 첨부 창에도 나타난다(`MockStore` 의 `workspaceFiles`, 시드는 `SEED_FILES`).
  붙인 파일은 입력줄 위 **칩**(원래 이름 + ×, 툴팁에 저장 경로)으로 보이고, 보내면 사용자 말풍선에 그대로 남는다(보내고 나면 입력줄 칩은 비운다). **첨부는 라우팅에도 쓰인다**: 문장에 팀 키워드가 없어도 **경로·확장자**(`.java`·`/test/`·`.tsx` 등)로 팀이 정해진다(`lib/executionSim.ts` 의 `FILE_ROUTES`). 실행 지시에는 ` · 첨부: 원래이름` 이 붙어 하위 실행까지 내려간다. 파일만 붙이고 보내면 지시는 "첨부한 파일을 확인해줘" 로 채운다.
- **실행(위임) 흐름**(`types.ts` 의 `Execution`, `lib/executionSim.ts`, `lib/executions.ts`, `pages/Projects/ExecutionTree.tsx`, `styles/execution.module.css`): 명령 하나가 실행 트리 하나가 된다(루트는 명령을 받은 에이전트 = 보통 마스터, `parentExecutionId` 로 자식). **상태**: `QUEUED` → `RUNNING` → (자식이 있으면) `WAITING_CHILD` → 자식이 모두 끝나면 다시 `RUNNING` → `DONE`. **판단이 필요한 실행(마스터·리더)은 계약**(`{action: 'delegate' | 'done'}`)을 남기고, 위임한 실행은 **위임 대상이 화면에 남는다**(마지막 `done` 으로 덮지 않는다). **라우팅은 규칙 먼저**(Rule Router): 요청에 팀 키워드가 걸리면 그 팀 리더에게 맡기고, 걸리는 팀이 없으면 마스터가 직접 처리한다. 리더는 팀이 크면(리더가 아닌 멤버가 2명 이상) 하위 한 명에게 한 번 더 넘기고, 작으면 직접 처리한다(그룹에 직접 보낸 명령도 같다). 채팅 응답 카드의 **실행 트리 보기**로 창(`ExecutionTreeModal`)을 열면 실행별 상태·계약·Handoff 요약·변경 파일·토큰·비용·시간·세션 id 를 본다. **계약·지표·세션 id 는 모두 모의 값**이고, 화면에 모의임을 밝히고 실제 출처(CLI `-p --output-format json` 의 `usage` / `total_cost_usd` / `duration_ms` / `session_id`)를 적는다. 재생 속도도 압축되어 있다(실제 도구는 실행마다 수십 초).
- **현실성 점검**(`pages/RealityCheck.tsx`, `/reality-check`): 실제 구현으로 갈 때 막히는 지점 9가지를 **흐름도 + 위험 카드**로 모아 본 화면. 내용은 그 파일의 `RISKS` 배열(데이터)이고, 항목마다 **결정**(여러 안 중 고른 것)과 **할 일**을 적는다. 위쪽에는 **확인된 사실**(`VERIFIED`: CLI `--help` 와 실측 1회로 확인한 세션 재개·`--json-schema`·계측값·호출 고정비·내장 서브에이전트)을 함께 둔다. 결정이 바뀌면 그 배열을 고친다.
- **구성도 캔버스**: 그룹은 상자, 에이전트는 노드로 그리고 그룹 안에서 리더가 위, 멤버가 아래에서 선으로 이어진다(배치는 `lib/canvasLayout.ts` 의 순수 함수). **에이전트는 그룹에 속하지 않아도 되며** 속하지 않은 에이전트는 "그룹 없음" 같은 이름표나 상자 없이 노드만 놓인다. 왼쪽 목록의 **에이전트 카드**와 **그룹 멤버 칩**은 **HTML5 DnD**(`lib/dnd.ts`)로 그룹 상자/그룹 카드에 넣거나 뺄 수 있고, **에이전트 카드를 캔버스 빈 곳에 놓으면 그 자리에 배치된다**(미배치는 배치되고, 그룹에 있던 것은 그룹에서 빠진다 — 캔버스의 드롭 처리가 `setAgentPlaced` + `setAgentPosition` 을 한다). **구성도 노드는 포인터로 끌어** 옮긴다(HTML5 DnD 를 쓰지 않는다). 배경 드래그로 이동, 휠로 확대/축소하고, **아래 가운데 뜬 도구**(−/+·배율·맞춤·위치 초기화)로 화면에 맞춘다(왼쪽·오른쪽 패널을 뺀 빈 곳의 가운데에 온다). **노드를 끌어 자유 위치로 옮길 수 있다**(그룹에 속하지 않은 노드만 자유 위치를 갖는다): 그룹 상자 위에 놓으면 그 그룹으로 들어가고, 빈 곳에 놓으면 그룹에서 빠져 그 자리에 선다. 자유 위치는 스토어의 `nodePositions` 에 쌓이고 `canvasLayout` 이 그 좌표를 쓴다(맞춤 크기에도 포함). **그룹 상자도 머리말을 끌어 통째로 옮긴다**(그 안의 노드·선도 함께, `groupPositions`). 그룹 상자 머리말의 **`⋮` 메뉴**에서 프롬프트 편집·삭제를 한다(왼쪽 그룹 카드와 같다). 손으로 옮긴 것은 **제자리를 차지한 채 좌표만 바뀌어** 다른 노드·그룹이 밀리지 않는다. 좌표는 **음수도 될 수 있어 캔버스 왼쪽·위까지 쓸 수 있고**, 맞춤은 경계 상자(`layout.minX`·`minY`)를 기준으로 한다. **위치 초기화** 버튼이 모두 지워 자동 배치로 되돌리고, 손으로 옮기기 시작하면 자동 맞춤은 멈춘다(`touchedRef`). 첫 멤버는 자동으로 리더가 되고, 한 에이전트는 여러 그룹에 속할 수 있다. 위치/크기는 CSS 변수(`--x`, `--y`, `--w`, `--h`, `--vx`, `--vy`, `--vs`)로만 넘기고 모양은 CSS 파일이 정한다.
- 현재 화면: Dashboard, 프로젝트(`/projects`, `/projects/:id`), 현실성 점검(`/reality-check`), 설정. 나머지 화면은 순서대로 추가한다.

## 실행(위임) 흐름 — 구현됨 (1차)

명령 하나가 **실행 트리 하나**다. 판단이 필요한 실행(마스터·그룹 리더)은 출력 끝에 **계약(JSON)** 을 남기고, 계약이 `delegate` 면 자식 실행을 만들어 결과를 기다렸다가 그 결과를 붙여 **다시 판단**한다. `done` 이 나오면 끝난다.

- **계약(출력 계약)**: 판단 실행은 `{"action":"delegate","targets":[{"agentId":9,"prompt":"…","expects":"…"}]}` 또는 `{"action":"done","summary":"…"}`, 작업 실행은 `{"summary":"…","changedFiles":["…"]}`. 스키마는 `delegation/ContractSchemas` 에 있고 **프롬프트 본문에 그대로 적어 준다**.
- **계약 처리 실패**: 파싱 실패 → **1회 재시도**(스키마를 다시 일러 준다) → **규칙 라우터**(팀 이름 토큰이 요청에 걸리면 그 팀 리더) → 그래도 안 되면 사람에게 넘긴다(`ExecutionService.markEscalated`, 실행은 `FAILED`).
- **상한**(`application.yaml` 의 `agentdock.delegation.*`, 코드에 박지 않는다): 깊이 3 · 트리당 실행 20건 · 실행당 판단 4회 · 한 번에 대상 4건 · **동시 CLI 4개**(공정 세마포어) · 트리 예산 5달러(스텝별 `--max-budget-usd` 로도 걸린다). 순환 위임(조상에게 되돌리기)은 거부하고 사유를 실행 로그(system)에 남긴다.
- **프롬프트 계층**: `project.masterPrompt` → `agent_group.prompt` → `agent.persona` 를 빈 줄로 이어 붙여 `--append-system-prompt` 로 넘긴다(`agent/PromptLayers`). 그룹 프롬프트는 그 에이전트가 **리더인 그룹 → 없으면 속한 첫 그룹** 것을 쓴다.
- **상태 표시**: 한 실행이 여러 스텝으로 이어지면 스텝이 끝나도 `SUCCEEDED` 로 두지 않는다 — `markRunning` 으로 되돌리고, 자식을 기다리는 동안은 `WAITING_CHILD`, 마지막에만 `markSucceeded`/`markEscalated`. **위임한 실행은 마지막 계약이 마무리여도 위임 대상(`delegatedTargetAgentId`)이 남는다**(누구에게 맡겼는지 화면에 남긴다).
- **위임한 트리의 Task 상태**는 트리가 전부 끝났을 때 **한 번만** `ExecutionFinishedEvent` 로 알린다(스텝마다 알리면 Task 가 일찍 끝난 것으로 표시된다).

### 워크트리 격리 (실행 디렉터리)

**명령 하나(루트 실행)가 git worktree 하나를 갖는다.** 루트와 그 자식은 **같은** worktree 에서 돈다 — 자식의 편집을 부모가 봐야 하기 때문이다. 서로 다른 명령(다른 루트)만 서로 격리된다. `WorktreeService`(`execution/service`)가 담당하고, `DelegationService.start` 가 루트 실행 행을 만든 직후에 만든다.

- **위치**: 저장소 **밖** — 워크스페이스 폴더의 형제 폴더 `<워크스페이스폴더>-wt/<루트실행id>` (예: `C:\Users\chey.kim\Documents\GitHub\AgentDock-wt\42`). 브랜치는 `agentdock/exec-<루트실행id>`. **저장소 안에 만들지 않는다**(`git status` 와 첨부 파일 목록을 더럽힌다).
- **언제/어떻게**: 워크스페이스가 git 저장소면 `git worktree add -b <브랜치> <경로>` 로 만들고, 그 트리 전체가 **cwd = worktree 경로** 로 돈다(`ExecutionGuard.Target.cwd()`, `--add-dir` 이 필요 없다). 자식은 부모의 worktree 경로·브랜치를 물려받고(`ExecutionFactory`), 루트에는 `execution.worktree_path`/`worktree_branch` 로 기록된다. 응답에는 `ExecutionResponse`(따라서 트리 노드 `execution`)에 실린다.
- **실패는 실행을 막지 않는다**: git 저장소가 아니거나 git 이 실패하면 워크스페이스에서 그대로 돌리고 SYSTEM 로그에 사유를 남긴다. 성공하면 `⎿ 워크트리: <경로> (브랜치 <브랜치>)`, 실패하면 `⎿ 워크트리를 만들지 못해 워크스페이스에서 실행합니다: <사유>`.
- **트리 끝에 결과를 모아 되돌린다(커밋 + 자동 병합)**: 트리(루트 실행)가 끝나면 `WorktreeService.collect` 가 그 워크트리의 변경을 트리 브랜치에 **커밋 하나**로 남긴다. 커밋 제목은 루트 실행의 최종 요약(계약 `done.summary` / `result_text`)이고 본문은 실행 id·참여 에이전트 이름 목록·변경 파일 수다(**`Co-Authored-By` 트레일러는 붙이지 않는다**). 커밋할 것이 없으면 빈 커밋을 만들지 않고 `NONE` 이다.
  - **메인 저장소로 병합**: 메인 작업 트리가 깨끗하면(`git status` 가 비었으면) `git merge --no-ff <트리브랜치>` 로 되돌려 `MERGED`. 더럽거나 충돌하면 `git merge --abort` 로 **절대 반쯤 병합된 상태를 남기지 않고** `MANUAL`(사유 + 직접 병합할 브랜치)로 넘긴다. 병합은 여러 트리가 동시에 끝날 수 있어 **전역 직렬화**(공정 세마포어)한다.
  - **삭제는 여전히 사람이 한다**: 워크트리·브랜치의 **자동 삭제는 없다**. 정리는 `DELETE /executions/{id}/worktree`(`?branch=true` 면 브랜치도 삭제, 204). 아직 도는 실행(`PENDING`/`RUNNING`/`WAITING_CHILD`)이면 **409**. 지운 뒤에는 실행의 경로·브랜치를 비운다(두 번째 호출은 404).
  - **기록**: 커밋 sha·병합 상태·사유·변경 파일을 **루트 실행에만** 남기고(V14) 응답(`ExecutionResponse`, 트리 노드)에 실린다. SYSTEM 로그: `⎿ 변경 N개를 커밋했습니다 (<sha>)` 다음에 `⎿ 메인 저장소(<브랜치>)로 병합했습니다` 또는 `⎿ 자동 병합하지 못했습니다(사유). 브랜치 <브랜치> 를 직접 병합하세요`.
- **셸을 쓰지 않는다**: git 도 `ProcessService`(내부적으로 `Executables`)로 실행한다. 프롬프트 규칙(마스터·그룹 프롬프트에 넣는다): "한 트리 안에서 같은 파일을 동시에 고치지 말고 나눠 맡긴다. 워크트리 병합은 사람이 판단한다." 에 더해 "작업이 끝나면 변경 사항을 트리 브랜치에 커밋하고, 메인 저장소가 깨끗하면 자동으로 병합한다. 병합하지 못하면 사유와 브랜치를 사용자에게 알린다." 를 덧붙인다. 뒤 문장이 앞 문장의 "사람이 판단한다"를 좁힌다 — **깨끗할 때만 자동으로 병합**하고, 아니면 사유와 브랜치만 알려 사람이 병합한다.
- **CLI 권한 모드 주의(실측)**: 에이전트의 `mode`(=`--permission-mode`)가 파일 쓰기를 막는 기본값이면 무인 실행에서 CLI 가 쓰기를 거부해 **트리에 변경이 남지 않고 `NONE` 으로 끝난다**(실제로 확인). 파일을 고쳐야 하는 에이전트는 `acceptEdits` 같은 쓰기 허용 모드를 지정한다(모드는 DB 데이터다).
- **화면**: 터미널 창 머리말에 브랜치와 커밋 sha, 실행 트리 창의 각 행에 브랜치가 보이고, 트리 **루트 행**에는 병합 상태 배지(병합됨 / 수동 병합 필요)·커밋 sha·변경 파일 목록(`A path` 등)과 정리 버튼이 있다. 채팅 **실행 요약 카드**(`ExecutionSummaryCard`)에도 같은 결과 한 줄이 붙는다(`apps/frontend`, 기존 토큰·CSS 모듈 클래스만 쓴다).

### 위임 프롬프트는 파일로 넘긴다

지시가 길어질수록 CLI 입력이 커져 토큰이 낭비되고 기록이 읽기 어려워진다. 그래서 **자식 실행의 전체 지시와 부모의 재판단 문맥은 프롬프트에 인라인하지 않고 마크다운 파일로 넘긴다**(`delegation/util/DelegationPrompts` 가 문자열을, `delegation/service/DelegationService` 가 파일 쓰기를 맡는다).

- **지시 파일**: 자식 실행을 만들 때 전체 지시를 마크다운 한 장(절: **원래 요청 / 맡은 일 / 기대 결과 / 지난 결과 / 첨부 파일**)으로 만들어 **작업 디렉터리(cwd — worktree 가 있으면 그 안, 없으면 워크스페이스) 기준** `<cwd>/.agentdock/prompts/<자식실행id>.md` 에 UTF-8 로 쓴다(`java.nio.file`, 셸 미경유). 빈 절(기대 결과·지난 결과·첨부)은 넣지 않는다.
- **자식 프롬프트(짧다)**: 파일을 가리키는 1~3줄만 넣는다 — `지시 파일: .agentdock/prompts/42.md — 이 파일을 먼저 읽고 그 내용대로 작업하세요. 기대 결과: <expects>`(expects 가 없으면 그 부분을 뺀다). 위임받은 판단 자식도 같은 지시 파일 참조를 지시로 받고, **팀 로스터·판단 규칙·계약 스키마는 짧으므로 그대로 인라인**한다.
- **재판단 문맥**: 부모가 자식 결과를 붙여 다시 판단할 때 그 문맥(자식 결과·사람의 답)은 `<cwd>/.agentdock/prompts/<실행id>-step<N>.md` 로 쓰고 스텝 프롬프트에는 참조 한 줄만 넣는다.
- **폴백 — 실패가 실행을 막지 않는다**: 파일을 쓰지 못하면(권한·경로 오류) 사유를 남기고 예전처럼 지시·문맥을 프롬프트에 인라인한다.
- **로그(SYSTEM)**: 파일마다 `⎿ 지시 파일: .agentdock/prompts/42.md (1,240자)`, 스텝마다 실제로 보낸 프롬프트 크기를 `⎿ 프롬프트 240자`.
- **gitignore**: 이 파일들은 워크트리 안에 생긴다 — 저장소 루트 `.gitignore` 에 **`.agentdock/`** 을 둬 프롬프트·첨부가 작업 트리를 더럽히거나 자동 병합을 막지 않게 한다.

### 실행 상태와 컨텍스트 (취소·만료·세션·그룹 노트)

- **취소는 자손까지**: `POST /executions/{id}/cancel` 은 그 실행과 **자손 전부**의 CLI 프로세스를 끊고 `CANCELLED` 로 전이한다(부모만 멈추면 자식 CLI 가 계속 돈다). 이미 끝난 실행의 상태는 건드리지 않지만 그 아래에서 도는 것은 멈춘다.
- **입력 대기 만료**: `WAITING_INPUT` 이 오래(기본 12시간, `agentdock.delegation.waiting-input-ttl-minutes`) 방치되면 10분 주기 검사(`@Scheduled`, 앱의 `@EnableScheduling`)가 만료로 취소한다. 만료도 취소와 같은 경로라 자손까지 정리된다. **기동 시 고아 정리에서 `WAITING_INPUT` 은 제외**한다(사람이 답할 차례를 없애면 안 된다).
- **세션 재사용**: 같은 실행의 다음 스텝은 자기 CLI 세션을 이어받는다(Claude Code `--resume`, cmdc `--resume`). 자식 분기는 Claude 의 `--fork-session` 만 — cmdc 에는 확인된 분기 플래그가 없어 자식은 새 세션으로 시작한다(맥락은 지시 파일·그룹 노트가 맡는다).
- **그룹 공유 노트**: `agent_group.shared_note` 는 규칙이 아니라 **맥락**이다. 그룹 실행의 시스템 프롬프트 맨 뒤에 실리고, 트리가 끝나면 `#<실행id> <요약>` 한 줄이 자동으로 붙는다(최근 2000자 유지). 화면에서는 그룹 프롬프트 창에서 본다 — 수정 요청에 `sharedNote` 를 보내지 않으면 기존 값을 지킨다.
- **계약 수리**: 계약 JSON 이 조금 망가져도(군더더기 쉼표·홑따옴표·스마트 따옴표·주석) `JsonObjects.repair` 가 건져낸다. 원문이 그대로 파싱되면 수리본은 쓰지 않는다.
- **프롬프트 규칙(DB, V19·V20)**: 자기 자신이나 부모에게 되돌려 맡기지 않는다(순환은 거부된다) / 작업 디렉터리 밖은 읽지도 쓰지도 않는다(밖에 있는 것이 필요하면 사람에게 물어 위치를 확인한다).

### CLI 출력을 다루는 법 (실측으로 확인한 사실 — 추측 금지)

- `--output-format stream-json`(+`--verbose`)으로 실행하고 **JSONL 한 줄씩** 받아 로그로 바꾼다(`runtime/ClaudeStreamJson`). `assistant` 이벤트의 content 블록(thinking/text/tool_use)이 로그 줄이 되고, 마지막 `result` 이벤트에서 결과 텍스트와 계측값을 꺼낸다(`usage`·`total_cost_usd`·`duration_ms`·`num_turns`·`session_id`·`is_error`).
- **프롬프트는 인자가 아니라 표준입력(UTF-8)으로 넘긴다**(`-p` 뒤에 값을 붙이지 않는다). 인자로 넘기면 JSON·따옴표·긴 문장이 CLI 에 온전히 전달되지 않는다.
- **`--json-schema` 는 쓰지 않는다.** Java 의 Windows 인자 인용 때문에 따옴표가 든 JSON 문자열이 전달되지 않는다(파일 경로로 넘기는 것도 거부된다). 계약 강제는 프롬프트로 하고, 결과 텍스트에서 JSON 객체를 찾아 파싱한다(코드블록·앞뒤 설명이 섞여도 찾아낸다).
- 계측값은 **CLI 가 준 값만** 저장한다(추정 금지). 값이 없으면 화면에 `-` 로 둔다.
- 실행의 최종 텍스트는 `execution.result_text`, Handoff 는 `execution.handoff`(JSONB)에 남긴다. 로그는 여전히 `execution_log` 에 줄 단위로 쌓고, 우리 쪽 안내(스텝 경계·위임 진행·거부 사유)는 **`LogStream.SYSTEM`** 으로 남긴다.
- SSE(`GET /executions/{id}/stream`)는 구독할 때 **지금까지의 로그를 먼저 재생**하고(메모리 버퍼, 넘치면 그 사실을 SYSTEM 줄로 알린다) 이어서 라이브로 보낸다. 실행이 끝나면 **`exit` 이름의 이벤트**(`{status, exitCode}`)를 보내고 닫는다 — 터미널 창을 나중에 열어도 전체가 보인다.

## Backend 모듈 (`apps/backend/src/main/java/com/agent/dock`)

**폴더 규칙(도메인 우선 + 계층 분해)**: 도메인을 먼저 두고 그 안을 계층으로 나눈다 — `domain/ · controller/ · service/ · repository/ · dto/ · util/`. **인터페이스(포트)는 그 도메인의 `interfaces/`** 에 모으고, 구현은 `service/` 등에 둔다(폴더 이름을 `interface` 로 쓸 수 없다 — Java 예약어다). Spring Data 리포지토리 인터페이스는 `repository/` 에 구현과 함께 둔다. 없는 계층 폴더는 만들지 않고, 도메인 안에 하위 도메인이 있으면(`provider/login`) 같은 규칙을 반복한다. **테스트는 대상 클래스와 같은 패키지**에 둔다(package-private 멤버 검증 유지).

- `common/` — `config/`(CORS·QueryDSL), `exception/`(400/403/404/409 예외 + `GlobalExceptionHandler`)
- `provider/` — `domain/`(AiProvider, ProviderKey, ConnectionStatus) · `controller/` · `service/`(AiProviderService, CliStatusService, CliRegistry, ProbeRegistry, ClaudeCodeCli, ClaudeCodeProbe, CommandCodeCli, CommandCodeProbe) · `repository/` · `dto/`(응답·요청·capabilities·CLI 상태·probe 결과) · `interfaces/`(AiRuntimeCli, AiRuntimeProbe). `provider/login/` 하위 도메인도 같은 규칙 — `controller/` · `service/`(LoginSessionService, LoginProcessFactory, PipeLoginProcess, LoginSession, LoginCommandRegistry, ClaudeCodeLoginCommand, CommandCodeLoginCommand) · `domain/`(SessionKind) · `dto/` · `interfaces/`(AiLoginCommand, LoginProcess) · `util/`(CommandShell)
- `agent/` — `domain/` Agent(프로젝트 소속, 페르소나) · `service/` AgentService + `AgentAvailability` · `util/` `PromptLayers`(프롬프트 계층 조립) · `repository/`(QueryDSL fetch join) · `dto/`
- `role/`, `permission/` — Role, PermissionProfile(도메인/서비스/리포지토리/DTO). `PermissionService.isAllowed(profile, action)` 가 enforcement primitive
- `workspace/` — `domain/`(Workspace, WorkspaceRuntimeStatus) · `service/`(WorkspaceService, WorkspaceRuntimeService) · `util/` `WorkspaceFs`(폴더 브라우징·UNC 차단) · `repository/` · `dto/`
- `project/` — `domain/`(Project, ProjectWorkspace) · `service/` ProjectService(`defaultWorkspace`, `updateMaster`, `updateDefaultWorkspace` — 기본 워크스페이스를 바꿀 때는 기존 기본을 먼저 내려야 부분 유니크 인덱스를 위반하지 않는다) · `repository/` · `dto/`
- `group/` — `domain/`(AgentGroup, AgentGroupMember) · `service/` GroupService · `repository/` · `dto/`
- `task/` — `domain/`(Task, TaskStatus) · `service/` TaskService(작업 실행·채팅 명령) · `repository/` · `dto/`
- `process/` — `service/` `ProcessService`(ProcessBuilder 래퍼) · `util/` `Executables`(PATH/PATHEXT 해석)
- `runtime/` — `interfaces/` `AgentRuntime` · `service/`(ClaudeCodeRuntime, CommandCodeRuntime, RuntimeRegistry) · `dto/`(AgentExecutionRequest/Result, ExecutionMetrics) · `util/` `ClaudeStreamJson`(stream-json → 로그·계측) · `CommandCodeStreamJson`(cmdc NDJSON → 로그·계측) · `JsonObjects`(결과 텍스트에서 JSON 객체 찾기 — Claude/Command Code 공용)
- `execution/` — `domain/`(Execution, ExecutionStatus, ExecutionDecision, ExecutionLog, LogStream) · `controller/` · `service/`(ExecutionService, ExecutionGuard, ExecutionFactory, ExecutionRunner, ExecutionStreamHub, **WorktreeService**) · `repository/` · `dto/`(응답·트리·이벤트 등)
- `delegation/` — `controller/`(ProjectCommandController) · `service/`(DelegationService, RuleRouter) · `dto/`(DelegationContract) · `util/`(ContractSchemas, DelegationPrompts)
- `event/` — 전역 데이터 변경 스트림. `controller/`(`GET /events/stream`) · `service/`(EventStreamService 허브, EventPublisher) · `dto/`(`DataChangedEvent`, `EventTypes`). 알림은 **페이로드가 아니다** — `{"type","projectId","executionId","taskId"}` 만 실어 보내고 화면이 바뀐 조각만 REST 로 다시 읽는다(타입: `execution.changed`·`task.changed`·`agent.changed`·`group.changed`·`project.changed`·`workspace.changed`·`runtime.changed`·`attachment.changed`). 구독 직후 `hello` 한 번, 이후 25초마다 하트비트 주석 줄. 발행은 **서비스 계층에서 데이터가 실제로 바뀌는 자리에만** 한다(예: `ExecutionService.update` 가 모든 상태·계약·결과 저장을 지나므로 그 한 곳에서 `execution.changed`). AOP 는 쓰지 않는다.

## DB 스키마

단일 진실은 **Flyway 마이그레이션**(`apps/backend/src/main/resources/db/migration/`, 현재 V1~V14)이며 컬럼명은 snake_case다. JPA는 `ddl-auto: validate` 로 일치만 검증한다.

테이블: `ai_provider`, `agent_role`, `permission_profile`, `workspace`, `workspace_runtime_status`, `project`, `project_workspace`, `agent_group`, `agent_group_member`, `agent`, `task`, `execution`, `execution_log`.

- `project.master_agent_id` / `project.master_prompt`, `agent_group.prompt`(V10): 프롬프트 계층(마스터 → 그룹 → 에이전트). 마스터는 프로젝트의 에이전트여야 하고, 응답에 이름을 함께 보여 주려고 `masterAgent` 관계는 즉시 로딩한다.
- `execution`(V10): `parent_execution_id`/`root_execution_id`(트리. **루트는 `root_execution_id` 가 비어 있다**), `decision`(`DELEGATE`/`DONE`), `delegated_target_agent_id`(마지막 계약이 마무리여도 남는다), `result_text`, `handoff`(JSONB), `input_tokens`/`output_tokens`/`cache_read_tokens`/`cache_creation_tokens`/`cost_usd`/`duration_ms`/`num_turns`/`session_id`(CLI 실측값), 인덱스 `idx_execution_root`/`idx_execution_parent`. 상태에 `WAITING_CHILD` 추가(제약은 없다 — 컬럼은 `VARCHAR(32)`).
- `execution.status` 값: `PENDING`(화면 "대기") · `RUNNING` · `WAITING_CHILD`(하위 대기) · `SUCCEEDED`(완료) · `FAILED` · `CANCELLED`.

- `ai_provider.enabled`(V6): 런타임 on/off. 시드는 `CLAUDE_CODE = true`.
- `workspace_runtime_status`(V6): `(workspace_id, provider_id)` 유니크. **폴더별** 런타임 상태(`CONNECTED`/`DISCONNECTED`/`ERROR`) + `last_checked_at`/`last_error`. Provider 단위 Connection 을 대체한다.
- `project_workspace`(V7): 프로젝트↔워크스페이스 N:N. `is_default` 는 프로젝트당 1개(부분 유니크 인덱스). 기존 `project.workspace_id` 는 V7 에서 이관 후 제거됐다.
- `agent.project_id NOT NULL`, `agent.persona TEXT`(V7). `agent.connection_id` 와 `ai_connection` 테이블은 V8 에서 제거됐다.
- `agent_group` 은 SQL 예약어 `group` 회피용 이름. `task` 는 `group_id`/`agent_id` 중 정확히 하나만 갖는다(CHECK + 서비스 검증).
- `execution.workspace_id` 는 실행 디렉터리(프로젝트 기본 워크스페이스)를 기록한다. 실제 작업 디렉터리는 격리됐으면 `worktree_path`(아래 V13)다. `task.workspace_id` 는 아직 없다(필요해지면 추가).
- `execution.worktree_path`(`VARCHAR(1024)`) / `worktree_branch`(`VARCHAR(200)`)(V13, 멱등): 그 실행 트리가 도는 git worktree 경로와 브랜치(`agentdock/exec-<루트실행id>`). 루트가 만들고 자식이 물려받는다. 격리하지 못했으면 비어 있고, 정리하면 다시 비운다.
- `execution.result_commit`(`VARCHAR(64)`) / `merge_status`(`VARCHAR(20)`, `MERGED`/`MANUAL`/`NONE`) / `merge_detail`(TEXT) / `changed_files`(JSONB)(V14, 멱등): 트리(루트 실행)가 끝나 그 워크트리를 커밋해 메인 저장소로 되돌린 결과(**루트 실행에만** 채운다). `changed_files` 는 `{status, path}` 배열이다. `merge_status` 는 `MERGED`(자동 병합) / `MANUAL`(사람이 병합해야 함) / `NONE`(커밋할 변경 없음) 중 하나다.
- `ai_provider.capabilities`(JSONB): `{"models": [...], "modes": [...], "notes": "..."}`. V5 시드, 화면에서 편집.

## 런타임 연결과 자격증명

- 자격증명은 **CLI 의 기존 로그인 세션을 그대로 사용**한다(스펙 6장 우선순위 1). 앱은 토큰/키를 저장하지 않는다(Connection 계열 컬럼은 V8 에서 제거).
- 실행 바이너리는 환경변수 override: `CLAUDE_CODE_BIN`(기본 `claude`), `COMMAND_CODE_BIN`(기본 `cmdc`). probe 와 로그인 명령이 같은 변수를 쓴다.
- **폴더별 상태 확인(probe)**: `POST /workspaces/{id}/runtimes/{providerId}/check` 가 **그 폴더를 작업 디렉터리로** CLI 를 짧은 프롬프트로 한 번 실행해(최대 30초) `status`/`last_error`/`last_checked_at` 을 저장한다. 로그인은 전역이지만 "이 폴더에서 실제로 실행되는가"는 폴더마다 다를 수 있어 이렇게 확인한다.
- probe 구현체는 런타임별 하나이고 `ProbeRegistry` 에 자동 등록된다. 현재는 `ClaudeCodeProbe`(CLAUDE_CODE)와 `CommandCodeProbe`(COMMAND_CODE)가 있다. 나머지 런타임은 목록에 표시되고 확인 시 ERROR("이 런타임은 아직 확인을 지원하지 않습니다") 가 된다.
- **웹 로그인 패널**: `POST /ai-providers/{id}/login` 이 서버 고정 로그인 명령(현재 `claude auth login`)을 실행하고 `GET /ai-providers/command-sessions/{sessionId}/stream`(SSE)으로 출력을, `POST .../input` 으로 stdin 입력을 전달한다. 런타임당 활성 세션 1개(로그인/설치 별개), 유휴 5분이면 종료. 로그인 명령은 사용자 입력으로 만들지 않는다.

### CLI 설치와 실행 파일 해석

- **실행 파일 해석**(`process/Executables`): Java 는 `npm` 처럼 확장자 없이 쓰는 이름을 `npm.cmd` 로 찾지 못한다(`CreateProcess error=2`). PATH/PATHEXT 를 훑어 실제 파일을 찾아 실행한다. `ProcessService`(에이전트 실행)와 probe 에 적용하며 **셸을 경유하지 않는다**(프롬프트가 셸로 해석되지 않게). `resolve` 는 찾으면 경로·없으면 입력 그대로, `locate` 는 찾으면 경로·없으면 null(설치 여부 판단용).
- **CLI 상태 조회**(`GET /ai-providers/{id}/cli` → `{resolvedPath, version, runnable, detail}`): `Executables.locate` 로 경로를 찾고, 있으면 `<실행 파일> --version` 을 10초 제한으로 실행해 첫 줄을 읽는다(`CliStatusService`, **셸 미경유**, 저장 안 함). 런타임별 `AiRuntimeCli` 구현이 등록된 런타임만 경로/버전을 보여 주고 나머지는 "CLI 정보를 확인할 수 없습니다"가 된다.
- **CLI 확인 창**(`CliPanel`): CLI 를 쓰는 모든 자리(에이전트 설정 카드 / 워크스페이스 런타임 행 / 에이전트 목록)에서 `CLI 확인` 버튼 하나로 연다. 창에서 위 상태와 "이 폴더에서 확인"(probe, `workspaceId` 있을 때만)·로그인·설치를 함께 실행하고, 출력 스트리밍은 `CommandPanel`(SSE)을 재사용한다.
- **설치 계획(데이터)**: `capabilities` 에 `installRequire`(먼저 있어야 하는 실행 파일, 예: `npm`), `installPrerequisite`(없을 때 먼저 실행할 단계, 예: `nvm install lts`), `install`(본 설치 단계) 을 둔다. V9 가 4종 런타임에 시드하며 화면에서 편집한다(추측 금지, npm 패키지는 실존 확인 후 기재).
- **설치 세션**: `POST /ai-providers/{id}/install` → 단계를 순서대로 실행하고 출력을 같은 SSE 로 스트리밍한다. 필수 도구가 없으면 선행 단계를 먼저 실행한다. 설치 단계만 `CommandShell`(`cmd.exe /c`, Unix `sh -c`)로 감싼다 — `.cmd`/`.ps1`/`&&` 를 쓰기 위함이며, **에이전트 실행 경로는 셸을 쓰지 않는다**.
- **CLI 없음 감지**: probe 가 실행 파일을 못 찾으면 `cliMissing` 으로 표시하고(저장: `workspace_runtime_status.cli_missing`) CLI 확인 창이 설치 버튼을 띄운다.

### 모델과 모드 (`capabilities`)

- 런타임별 지원 모델/모드 목록은 **코드가 아니라 데이터**다(`ai_provider.capabilities`). 화면("에이전트 설정" 카드의 "모델/모드 편집")에서 갱신한다(정규화: trim·빈값·중복 제거, 다른 키 보존).
- Claude CLI 는 모델 목록을 알려주는 명령을 주지 않는다(`claude models` 는 프롬프트로 처리된다). 자동 수집 불가 → 수동 편집. **Command Code 는 `cmdc --list-models` 가 모델 목록을, `cmdc --help` 의 `--permission-mode` 가 모드 4종(standard·plan·accept-edits·yolo)을 준다.** V5 가 `claude --help` 에 나온 alias(`opus`/`sonnet`/`fable`)와 `--permission-mode` 6종을 시드한다(추측 금지 원칙).
- `Agent.model` → `--model`, `Agent.mode` → `--permission-mode`, `Agent.persona` → **`--append-system-prompt`**(기본 시스템 프롬프트에 덧붙임). 값이 없으면 플래그를 붙이지 않는다.
- 목록에 없는 값도 막지 않는다(최종 판단은 CLI). 화면은 datalist 로 제안하고 경고만 표시한다.

### Command Code(`cmdc`) 런타임

Claude Code 외에 처음 추가한 **두 번째 런타임**이다(2026-10-01, Command Code v1.73.2 실측). 같은 인터페이스를 구현하고 `@Component` 로만 등록한다(`ClaudeCodeRuntime` 과 동형) — 런타임 `CommandCodeRuntime`(COMMAND_CODE) · probe `CommandCodeProbe` · 로그인 `CommandCodeLoginCommand`(`cmdc login`) · CLI `CommandCodeCli`.

- 비대화형은 `-p --output-format json` — **NDJSON 이벤트 스트림 + 마지막 result 한 줄**이다. 프롬프트는 표준입력(UTF-8)으로 넘긴다(`-p` 뒤에 값을 붙이지 않으면 stdin 을 읽는다).
- 런타임 인자는 `-p --output-format json -t` + (있으면) `--model`·`--permission-mode`. **`-t`(프로젝트 자동 신뢰)는 항상 붙인다** — 무인 실행이 첫 권한 프롬프트에서 멈추지 않게(probe 도 같다).
- result 줄은 **camelCase** — `sessionId`·`usage.inputTokens/outputTokens/cacheReadTokens/cacheWriteTokens`·`durationMs`·`stopReason`·`finalText`. **비용 필드가 없다 → `costUsd` 는 null(추정 금지).** 로그로 남기는 이벤트는 `model_request_start`(system)·`thinking_end`·`message_end` 의 text 블록이고, delta·중복 이벤트는 버린다(`CommandCodeStreamJson`).
- 확인된 플래그(`cmdc --help`): `-p/--print [query]` · `--output-format text|json` · `-t/--trust` · `-m/--model` · `--permission-mode standard|plan|accept-edits|yolo` · `--list-models` · `--effort` · `--max-turns` · `--tools-all` · `--config key=value` · `--local-only` · `-w/--worktree` · `-r/--resume` · `-c/--continue` · `--session` · `--no-session` · `-n/--name` · `--skip-onboarding` · `--no-auto-update`. **시스템 프롬프트/예산 플래그는 확인되지 않아 붙이지 않는다** — 페르소나 계층은 프롬프트 본문으로만 전달된다.

## Permission Enforcement

`permissionProfile` 의 boolean 플래그로 저장한다. Execution 을 만드는 모든 경로(`POST /executions`, `POST /tasks/{id}/run`)는 `ExecutionService` 를 지나며, `PermissionService.isAllowed(profile, TERMINAL_EXECUTE)` 가 false 면 Runtime 을 호출하지 않고 **403** 으로 거부한다.

그 직후 **연결 가드**가 한 번 더 막는다. `AgentAvailability.evaluate(providerDeleted, runtimeEnabled, workspaceStatus)` 판정으로:

| 조건 | 409 사유 |
| --- | --- |
| 런타임이 논리 삭제됨 | `PROVIDER_DELETED` |
| 런타임이 꺼져 있음(`enabled=false`) | `RUNTIME_DISABLED` |
| 프로젝트 기본 워크스페이스에서 그 런타임이 `CONNECTED` 아님(확인한 적 없음 포함) | `CONNECTION_NOT_CONNECTED` |

- 작업 디렉터리는 **프로젝트의 기본 워크스페이스**이며, 할당이 없으면 409(`Project N has no workspace assigned`).
- 새 실행만 차단하고 이미 실행 중인 Execution 은 건드리지 않는다. Agent 응답에는 파생값 `available`/`unavailableReason` 이 있고 저장하지 않는다.
- 사용 불가 에이전트는 `PUT /agents/{id}/provider` 로 다른(켜져 있는) 런타임을 재할당해 복구한다.
- `Agent.mode`(권한 문자열)는 이 가드와 별개로 CLI 에 전달될 뿐이다.

## 실행 방법

### 사전 준비

- JDK 25: 이 개발 환경에서는 `C:\Users\chey.kim\.jdks\openjdk-25.0.2` 에 있고 PATH 에 없다.

```powershell
$env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

- Postgres 접속 정보는 OS 환경변수(`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`). 기본값 `jdbc:postgresql://localhost:5432/AGENT_DOCK`, `postgres`, 빈 비밀번호.

```
createdb AGENT_DOCK        # 최초 1회
npm install                # 프론트 의존성
npm run dev:backend        # 8080 (Gradle bootRun, 기동 시 Flyway 자동 적용)
npm run dev:frontend       # 3030 (Vite)
```

Flyway 마이그레이션은 `V<N>__<설명>.sql` 새 버전으로만 추가한다(기존 파일 수정 금지).

### AI 에이전트가 검증할 때의 포트

사용자 포트는 백엔드 `8080` / 프론트 `3030` 이다. 에이전트는 **8081 / 3031** 만 쓰고 끝나면 즉시 종료한다.

```
npm run dev:backend:test    # 8081
$env:VITE_API_BASE='http://localhost:8081'; npm run dev:frontend:test   # 3031
```

DB 확인: `C:\Program Files\PostgreSQL\17\bin\psql.exe -U postgres -d AGENT_DOCK -c "..."`

## 코딩 컨벤션

- 코드/식별자는 영어, 커밋 메시지·문서 설명은 한국어/영어 혼용 가능
- 새 Runtime/Probe/로그인 명령은 인터페이스(`AgentRuntime`/`AiRuntimeProbe`/`AiLoginCommand`)만 구현하고 `@Component` 로 등록한다. 레지스트리가 `List<...>` 주입으로 자동 색인하므로 기존 모듈을 고치지 않는다.
- 요청은 `record` + Jakarta Validation, 응답은 엔티티 대신 `record` Response DTO(Jackson 순환 방지). Controller 는 얇게, 로직은 Service 에.
- shadow FK 필드(`insertable=false, updatable=false`)는 저장 직후 비어 있다. 생성 응답에서는 관계로 보완하거나(기존 패턴) `@Transactional` 없이 커밋 후 새로 조회한다(`AgentService.assignProvider`).
- 오래 걸리는 작업(probe, 로그인 세션)에는 `@Transactional` 을 붙이지 않는다. 필요한 관계는 fetch join 으로 조회 시점에 함께 읽는다.
- POST 는 **201**, 삭제는 **204**(프론트 `request()` 가 204 를 따로 처리).
- 변하지 않는 규칙(권한, 런타임 enabled, 폴더 상태)은 백엔드가 강제하고, 자주 변하는 값(모델/모드)은 데이터로 두고 CLI 에 위임한다.
- **실시간 갱신은 이벤트 스트림(SSE) 우선, 폴링은 보조(느린 주기·포커스 시)로만.** 화면은 `GET /events/stream` 을 한 번 구독해 "무엇이 바뀌었는지"를 받고 **바뀐 조각만 기존 REST 로 다시 읽는다**(주기적 전체 폴링 금지 — 3초마다 ~20건을 부르던 방식은 쓰지 않는다). 새 화면·새 갱신도 이 규칙을 따른다.

## 브랜치 흐름과 커밋 컨벤션

브랜치 흐름은 `기능 브랜치 → dev → test → release` 다. `dev`는 통합 브랜치이고, `test`와 `release`는 `dev`에서 병합해 만든다. 따라서 **`dev`에 쌓이는 커밋 이력이 곧 릴리스 노트의 원천**이고, 커밋 메시지 규칙이 두 단계로 나뉜다.

### 1) 기능 브랜치 — 글로벌 컨벤션([Conventional Commits](https://www.conventionalcommits.org/))

```
feat: 에이전트 실행에 permission enforcement 추가
fix(workspace): 폴더 선택에서 드라이브 루트 조회가 멈추던 문제 수정
```

- 타입: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- scope 는 선택: `fix(workspace): ...`. 제목은 소문자 타입 + 콜론 + 한 칸 + 설명(한국어 허용)
- 하나의 커밋은 하나의 논리적 단위로 나눈다.

### 2) `dev`에 반영되는 커밋 — squash merge + `[Type] 제목`

```
[Feat] NestJS/Next.js → Spring Boot + Vite React 스택 전환
[Fix] 폴더 선택에서 드라이브 루트 조회가 멈추던 문제 수정
```

- 대괄호 + 대문자 시작: `[Feat]` `[Fix]` `[Docs]` `[Style]` `[Refactor]` `[Perf]` `[Test]` `[Build]` `[Ci]` `[Chore]` `[Revert]`
- scope/콜론 형식은 쓰지 않는다. 기능 브랜치의 개별 커밋은 squash 되므로 `dev` 이력은 항상 `[Type]` 형식이다.
- Breaking change 는 본문에 `BREAKING CHANGE: <설명>`.
- AI 에이전트가 만든 커밋에도 `Co-Authored-By:` 트레일러를 붙이지 않는다.
