# Tasks·설정 화면 디자인 스펙 (2026-10-08)

AgentDock 의 신설 **Tasks/실행 목록** 화면(`apps/frontend/src/pages/Tasks` 예정)과 **설정** 화면(`apps/frontend/src/pages/Settings`) 설계 문서. 담당별 절로 나뉘며 **각 담당은 자기 절만** 채운다.

절 담당:

| 절 | 담당 |
| --- | --- |
| `레이아웃` | UI 디자인 담당(실행 232) |
| `상태별 표현` · `인터랙션` | UX 담당(실행 234) |
| `사용 토큰` | 디자인 시스템 담당(실행 233) |

**대전제**: 색·간격·반경·그림자는 `apps/frontend/src/styles/tokens.css` 의 **기존 토큰만** 쓴다. 새 토큰을 tokens.css 에 임의로 추가하지 않는다(아래 "3. 부족(신규 필요) 토큰" 참고). 토큰은 라이트(`:root`) · 다크(`:root[data-theme='dark']` 및 `prefers-color-scheme`) 세 곳에 같은 값으로 정의되어 있고, 아래 표의 값은 그 정의를 그대로 옮긴 것이다.

## 사용 토큰

화면을 만들 때 필요한 시각 요소를 **기존 토큰**에 매핑한 표다. 표의 "다크 값"은 `html[data-theme='dark']` 또는 시스템 다크에서 실제로 적용되는 값이다. "재사용 클래스"는 그대로 가져다 쓰거나(`composes`), 새 화면의 `.module.css` 로컬 클래스가 `composes: <공용> from '<경로>'` 로 가져올 대상이다(절대 경로는 `apps/frontend/src` 기준).

### 1. 표면 / 배경

| 요소 | 용도 | 토큰 (`var(--...)`) | 라이트 값 | 다크 값 | 재사용 클래스 (`composes`) |
| --- | --- | --- | --- | --- | --- |
| 화면 배경 | body 전체(메시 그라데이션) | `--app-bg` | `radial-gradient(...) , #eef0fb` | `radial-gradient(...) , #161616` | `globals.css` `body`(직접) |
| 기본 표면 | 카드·행·요약 상자의 면 | `--color-surface` | `rgba(255,255,255,0.55)` | `rgba(255,255,255,0.06)` | `shared.panel`, `execution.card/.row/.totals` |
| 옅은 강조/hover 면 | 행 hover·칩·코드 배경 | `--color-code-bg` | `rgba(75,93,240,0.08)` | `rgba(255,255,255,0.09)` | `menu.item:hover`, `execution.cardButton:hover`, `AgentList.card:hover` |
| 입력 면 | input·select·칩 안쪽 | `--color-input-bg` | `rgba(255,255,255,0.78)` | `rgba(255,255,255,0.08)` | `AgentList.searchWrap input`, `execution.answer` |
| 사이드바 면 | 왼쪽 아이콘 사이드바 | `--color-sidebar` | `#ffffff` | `#1b1b1b` | `Sidebar.module.css`(직접) |
| 글래스 패널 | 화면 위에 뜨는 패널·창 | `--glass-bg`, `--glass-bg-strong`, `--glass-border`, `--glass-highlight`, `--glass-blur` | `rgba(255,255,255,0.58)` / `0.84` / `rgba(255,255,255,0.8)` / inset 흰 하이라이트 / `18px` | `rgba(66,66,70,0.68)` / `rgba(76,76,82,0.95)` / `rgba(255,255,255,0.2)` / inset 밝은 하이라이트 / `18px` | `glass.glass`, `glass.glassStrong` |
| 오버레이 | 모달 뒤 어둡게 + 블러 | `--color-overlay` | `rgba(20,22,50,0.35)` | `rgba(0,0,0,0.6)` | `modal.overlay` |

### 2. 텍스트 계층

| 요소 | 용도 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- | --- |
| 기본 텍스트 | 본문·제목 | `--color-text` | `#161a33` | `#ececec` | (전역 상속), `modal.modal` |
| 보조 텍스트 | 설명·라벨·메타·타임스탬프 | `--color-muted` | `#616680` | `#a6a6a6` | `shared.muted`, `shared.hint` |
| 강조 텍스트 | 링크·선택 항목·활성 | `--color-primary` | `#4b5df0` | `#ececec` | `execution.md a`, `AgentList.cardSelected` |
| primary 버튼 글자 | 채워진 버튼 위 글자 | `--color-on-primary` | `#ffffff` | `#111111` | `Settings.statusPillOn/Off`, `AgentList.statusWorking` |
| 링크 hover/포커스 | primary 버튼 hover | `--color-primary-hover` | `#3c4ddb` | `#ffffff` | (전역 `button:hover` 는 `filter`) |

### 3. 테두리 / 간격 / 반경 / 그림자

| 요소 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- |
| 기본 경계 | `--color-border` | `rgba(30,36,90,0.1)` | `rgba(255,255,255,0.12)` | `shared.panel`, `execution.row` |
| 강한 경계(입력·배지) | `--color-border-strong` | `rgba(30,36,90,0.2)` | `rgba(255,255,255,0.22)` | `shared.badge`, `Settings.ghostButton` |
| 글래스 경계 | `--glass-border` | `rgba(255,255,255,0.8)` | `rgba(255,255,255,0.2)` | `glass.glass`/`glassStrong` |
| 선택·포커스 경계 | `--color-primary` | `#4b5df0` | `#ececec` | `AgentList.cardSelected`, `Settings.menuItemActive::before` |
| 간격(작) | `--spacing-sm` | `8px` | `8px` | 전역 `shared.actions`, `shared.statusRow` |
| 간격(중) | `--spacing-md` | `16px` | `16px` | `shared.panel`, `modal.modal` |
| 간격(대) | `--spacing-lg` | `24px` | `24px` | `Settings.card`, `execution.modal` |
| 반경(기본) | `--radius` | `10px` | `10px` | 입력·버튼·행·작은 상자 |
| 반경(큰) | `--radius-lg` | `16px` | `16px` | 카드·창·칩 컨테이너 |
| 알약 반경 | 리터럴 `999px` (토큰 없음) | `999px` | `999px` | `execution.badge`, `AgentList.status`, `Settings.statusPill` |
| 떠 있는 그림자 | `--shadow-float` | `0 10px 34px rgba(40,50,120,0.16), 0 0 0 1px rgba(30,36,90,0.06)` | `0 12px 36px rgba(0,0,0,0.55), 0 0 0 1px rgba(0,0,0,0.4)` | `glass.glass`/`glassStrong` |
| 글래스 하이라이트 | `--glass-highlight` | inset `0 1px 0 rgba(255,255,255,0.85)` | inset `0 1px 0 rgba(255,255,255,0.14)` | `glass.*` |
| hover 큰 그림자 | `--color-shadow` | `rgba(40,50,120,0.16)` | `rgba(0,0,0,0.5)` | `Projects.card:hover` |
| 선택/포커스 링 | 리터럴 `0 0 0 1px var(--color-primary)` / `0 0 0 3px var(--color-code-bg)` | - | - | `AgentList.cardSelected`, 전역 `:focus-visible` |

> 글래스 요소에는 `overflow` 를 두지 않는다(blur 가 둥근 모서리 밖으로 네모나게 새는 문제). 잘라야 하면 블러가 없는 안쪽 요소에서 자른다.

### 4. 상태색 (진행 / 완료 / 실패 / 경고 / 대기)

실행 상태와 요청 상태를 같은 토큰 언어로 매핑한다. 화면 문구는 실행(`CONVENTIONS.md`) 기준.

| 상태 | 화면 문구 | 색 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- | --- |
| `PENDING` | 대기 | `--color-muted` | `#616680` | `#a6a6a6` | `execution.badge`(중립), `AgentList.statusIdle` |
| `RUNNING` | 실행 중 | `--color-primary` (+ 채움 시 `--color-on-primary`) | `#4b5df0` / 글자 `#ffffff` | `#ececec` / 글자 `#111111` | `execution.active`, `AgentList.statusWorking` |
| `WAITING_CHILD` | 하위 대기 | `--color-warning` | `#b26a00` | `#e5b567` | `execution.wait`, `AgentList.statusWaiting` |
| `SUCCEEDED` | 완료 | `--color-muted`(조용히) / 강조 시 `--color-primary` | `#616680` | `#a6a6a6` | `execution.done`, `Toaster.success`(`--color-primary`) |
| `FAILED` | 실패 | `--color-error` (+ 면 `--color-error-bg`) | `#d6404a` / `rgba(214,64,74,0.12)` | `#ff7b6e` / `rgba(255,123,110,0.14)` | `execution.failed`, `shared.badgeError`, `Toaster.error` |
| `CANCELLED` | 취소 | `--color-muted`(권장) 또는 `--color-error` | `#616680` | `#a6a6a6` | `execution.badge`(중립). 실패와 같은 빨강을 원하면 `--color-error` |
| `WAITING_INPUT` | 입력 대기 | `--color-warning` (+ 면 `--color-warning-bg`) | `#b26a00` / `rgba(178,106,0,0.14)` | `#e5b567` / `rgba(229,181,103,0.16)` | `execution.question`, `RequestsPanel.rowActive`, `Toaster.warn` |
| `MANUAL`(수동 병합) | 병합 필요 | `--color-warning` (+ 면 `--color-warning-bg`) | `#b26a00` | `#e5b567` | `Toaster.warn`, `RequestsPanel.pill`(좌측 띠 `inset 3px 0 0`) |
| 진행률 바(채움/트랙) | 진행 N/M | 채움 `--color-primary` / 트랙 `--color-code-bg` | `#4b5df0` / `rgba(75,93,240,0.08)` | `#ececec` / `rgba(255,255,255,0.09)` | 없음(화면 로컬, `--radius` 알약) |

### 5. 배지

| 요소 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- |
| 중립 배지(테두리형) | `--color-border-strong` + `--color-muted` | `rgba(30,36,90,0.2)` / `#616680` | `rgba(255,255,255,0.22)` / `#a6a6a6` | `execution.badge`, `AgentList.status` |
| 성공/활성 배지 | `--color-primary` | `#4b5df0` | `#ececec` | `shared.badgeOk`, `execution.tag` |
| 경고 배지 | `--color-warning` | `#b26a00` | `#e5b567` | `execution.wait`, `AgentList.statusWaiting` |
| 실패 배지 | `--color-error` | `#d6404a` | `#ff7b6e` | `shared.badgeError`, `execution.failed` |
| 채움형 상태 배지 | bg `--color-primary` + 글자 `--color-on-primary` | `#4b5df0` / `#ffffff` | `#ececec` / `#111111` | `Settings.statusPillOn`, `AgentList.statusWorking` |
| 상태 점(●) | `currentColor`(배지 색 상속) | - | - | `Settings.statusDot` |
| 마스터/역할 태그 | bg `--color-code-bg` + 글자 `--color-primary` | `rgba(75,93,240,0.08)` / `#4b5df0` | `rgba(255,255,255,0.09)` / `#ececec` | `AgentList.master`, `execution.tag` |

### 6. 버튼 (primary / ghost / danger)

| 종류 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- |
| primary(채움) | `--gradient-accent` + `--color-on-primary` | `linear-gradient(135deg,#5b6cff,#8f5cff)` / 글자 `#ffffff` | `linear-gradient(180deg,#f4f4f4,#d6d6d6)` / 글자 `#111111` | 전역 `button`(globals) 그대로 |
| ghost(테두리·투명 면) | `--color-text` + `--color-border-strong`, hover 면 `--color-code-bg` | `#161a33` / `rgba(30,36,90,0.2)` | `#ececec` / `rgba(255,255,255,0.22)` | `Settings.ghostButton`, `execution.cardButton`, `shared.addButton` |
| danger(투명 + 빨강) | `--color-error`, hover 면 `--color-error-bg` | `#d6404a` / `rgba(214,64,74,0.12)` | `#ff7b6e` / `rgba(255,123,110,0.14)` | `shared.dangerButton`, `menu.danger` |
| 아이콘 전용(+/⋮) | 투명, hover 면 `--color-code-bg` | - | - | `shared.addButton`, `AgentList.menuButton` |
| 비활성 | 리터럴 `opacity:0.5`(전역) | - | - | 전역 `button:disabled` |

### 7. 입력 / 셀렉트

| 요소 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- |
| 입력 면 | `--color-input-bg` | `rgba(255,255,255,0.78)` | `rgba(255,255,255,0.08)` | `AgentList.searchWrap input`, `RequestsPanel.answerRow input` |
| 입력 경계 | `--color-border`(칸 안) / `--color-border-strong`(전역 기본) | `rgba(30,36,90,0.1)` | `rgba(255,255,255,0.12)` | `execution.answer`, 전역 `input/select` |
| 포커스 | 경계 `--color-primary` + 링 `--color-code-bg` | `#4b5df0` / `rgba(75,93,240,0.08)` | `#ececec` / `rgba(255,255,255,0.09)` | 전역 `:focus-visible`, `execution.answer:focus-within` |
| 플레이스홀더 | `--color-muted` | `#616680` | `#a6a6a6` | 전역 `::placeholder` |
| 셀렉트 화살표 | `--select-arrow` (내장 stroke = `--color-muted` 값) | stroke `#616680` | stroke `#a6a6a6` | 전역 `select`(`appearance:none` + 화살표) |
| 셀렉트 펼친 목록 | bg `--color-sidebar` + 글자 `--color-text` | `#ffffff` / `#161a33` | `#1b1b1b` / `#ececec` | 전역 `select option` |
| 필터 팝업 안 셀렉트 | 위와 동일 | - | - | `AgentList.popup select` |

### 8. 필터 칩

| 요소 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- |
| 칩 껍데기 | 경계 `--color-border-strong`, 글자 `--color-text`, 알약 `999px` | `rgba(30,36,90,0.2)` / `#161a33` | `rgba(255,255,255,0.22)` / `#ececec` | `attachment.chip` |
| 칩 hover/제거 | 글자 `--color-text`, 면 투명 | `#161a33` | `#ececec` | `attachment.chipRemove:hover` |
| 필터 버튼(켜짐) | bg `--color-primary` + 글자 `--color-on-primary` | `#4b5df0` / `#ffffff` | `#ececec` / `#111111` | `AgentList.filterButtonOn` |
| 필터 버튼(꺼짐) | 글자 `--color-muted`, 면 투명 | `#616680` | `#a6a6a6` | `AgentList.filterButton` |
| 필터 칩 그룹 간격 | `--spacing-sm` 갭 + `4/6px` 리터럴 | `8px` | `8px` | `attachment.chips` |

### 9. 표 / 행 hover

| 요소 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스 |
| --- | --- | --- | --- | --- |
| 표 머리글 면 | `--color-code-bg` | `rgba(75,93,240,0.08)` | `rgba(255,255,255,0.09)` | `execution.md th` |
| 셀 경계 | `--color-border` | `rgba(30,36,90,0.1)` | `rgba(255,255,255,0.12)` | `execution.md th/td` |
| 행 hover | 면 `--color-code-bg` | `rgba(75,93,240,0.08)` | `rgba(255,255,255,0.09)` | `menu.item:hover`, `AgentList.card:hover`, `RequestsPanel.row:hover` |
| 행 기본 경계/좌측 띠 | `--color-border` + 좌 띠 `--color-border-strong` | `rgba(30,36,90,0.1)` / `0.2` | `rgba(255,255,255,0.12)` / `0.22` | `execution.row` |
| 행 선택/활성 | 경계 `--color-primary` + 면 `--color-code-bg` | `#4b5df0` | `#ececec` | `RequestsPanel.rowActive`(경고), `AgentList.cardSelected` |
| 행 간격 | `--spacing-sm`(8px) / 리터럴 `2·4·6px` | `8px` | `8px` | `execution.tree`, `RequestsPanel.list` |

### 10. 글래스 패널

| 요소 | 토큰 | 라이트 값 | 다크 값 | 재사용 클래스(`composes`) |
| --- | --- | --- | --- | --- |
| 위에 뜬 패널(약) | `--glass-bg` 계열 | `rgba(255,255,255,0.58)` | `rgba(66,66,70,0.68)` | `glass.glass` |
| 읽는 면·창(강) | `--glass-bg-strong` 계열 | `rgba(255,255,255,0.84)` | `rgba(76,76,82,0.95)` | `glass.glassStrong` — `modal.modal`, `menu.menu`, `execution.modal`, `attachment.picker`, `Toaster.toast`, `AppStatus.status`, `AgentList.popup` 가 이미 `composes` |
| 창(모달) 틀 | `glassStrong` + `--radius-lg` + `--spacing-lg` | - | - | `modal.modal`(+`modal.wide`), `execution.modal`, `attachment.picker` |
| 팝업 메뉴 | `glassStrong` + `--radius` | - | - | `menu.menu`, `AgentList.popup` |
| 화면 위 상태 칩 | `glassStrong` + 좌측 띠 `inset 3px 0 0 <상태색>` | - | - | `AppStatus.status`, `RequestsPanel.pill`, `Toaster.toast` |

### 11. 화면 조립 예 (`composes` 방향)

새 화면의 로컬 클래스가 공용 클래스를 가져다 쓰는 방향을 명시한다(경로는 `pages/Tasks` 기준).

```css
/* pages/Tasks/Tasks.module.css - 예시 방향 */
.toolbar  { /* 필터바 */ }
.row      { composes: row from '../../styles/execution.module.css'; }   /* 행 기본 뼈대 */
.badge    { composes: badge from '../../styles/execution.module.css'; } /* 상태 배지 뼈대 */
.panel    { composes: glass from '../../styles/glass.module.css'; border-radius: var(--radius-lg); }
.popup    { composes: glassStrong from '../../styles/glass.module.css'; }
.empty    { composes: hint from '../../styles/shared.module.css'; }
.danger   { composes: dangerButton from '../../styles/shared.module.css'; }

/* pages/Settings/*.module.css - 기존 클래스 재사용 */
.card     { composes: glass from '../../styles/glass.module.css'; }     /* 이미 Settings.module.css 에 존재 */
.ghost    { composes: ghostButton from './Settings.module.css'; }
```

## 2. 재사용할 공용 CSS 모듈 클래스 목록

공용 모듈만 적는다(화면 전용 CSS 는 해당 화면 폴더에). `composes` 대상은 "가져올 때 쓰는 이름"이다.

| 모듈 (경로) | 클래스 | 제공/`composes` 대상 | 새 화면에서의 용도 |
| --- | --- | --- | --- |
| `styles/glass.module.css` | `.glass` | 반투명·블러 약 | 위에 뜬 패널, 카드 |
| | `.glassStrong` | 반투명·블러 강(읽는 면) | 모달·팝업·토스트·상태 칩 |
| `styles/shared.module.css` | `.panel` | 테두리+면+`--radius-lg`+`--spacing-md` | 본문 안 상자(설정 접기 패널 등) |
| | `.badge` / `.badgeOk` / `.badgeError` | 중립/성공/실패 배지 | 상태 배지 |
| | `.muted` / `.hint` | 보조 텍스트(12/13px) | 설명·빈 목록 안내 |
| | `.actions` / `.statusRow` | 버튼 줄 / 상태 한 줄 | 툴바·상태 줄 |
| | `.dangerButton` | 투명+빨강, hover `--color-error-bg` | 삭제·취소 |
| | `.addButton` | 아이콘 전용 버튼 | `+` 추가 |
| `styles/modal.module.css` | `.overlay` | 오버레이+블러 | 창 뒤 |
| | `.modal` (`composes: glassStrong`) | 창 틀 | 확인·편집 창 |
| | `.wide` | 넓은 창 | 실행 트리 등 |
| | `.field` / `.actions` | 라벨+입력 / 하단 버튼 줄 | 폼 |
| `styles/menu.module.css` | `.menu` (`composes: glassStrong`) | `⋮` 팝업 틀 | 행/카드 메뉴 |
| | `.item` / `.danger` | 항목 / 위험 항목 | 메뉴 항목 |
| `styles/execution.module.css` | `.badge` `.active` `.wait` `.done` `.failed` | 실행 상태 배지 5종 | 상태 배지 |
| | `.row` `.rowHead` `.rowName` `.rowAction` `.rowMetrics` | 실행 행 뼈대 | 목록 행 |
| | `.card` `.cardHead` `.cardButton` | 요약 카드 | 요약 |
| | `.totals` | 집계 상자 | 집계 줄 |
| | `.tag` | 알약 태그 | 역할/마스터 |
| | `.modal` (`composes: glassStrong`) | 실행 트리 창 | 행 클릭 창 |
| | `.question` `.questionWide` `.optionButton` | 입력 대기 질문 | 요청 처리 |
| | `.detail` `.md` `.kv` `.kvRow` `.kvKey` `.kvValue` `.raw` `.rawBody` | 상세(마크다운·키값·원문) | 실행 상세 |
| `styles/attachment.module.css` | `.chip` `.chipName` `.chipRemove` `.chips` | 알약 칩 | 필터 칩·첨부 칩 |
| `components/ConfirmDialog.module.css` | `.dialog` `.text` `.actions` | 확인 창 | 취소·정리 확인 |
| `components/Toaster.module.css` | `.toast` (`composes: glassStrong`) `.info` `.success` `.warn` `.error` | 토스트 4종 | 알림(종류별 1회) |
| `components/AppStatus.module.css` | `.status` (`composes: glassStrong`) `.loading` `.offline` `.error` `.retry` | 통신 상태 칩 | 로딩·끊김 |
| `pages/Projects/AgentList.module.css` | `.status` `.statusUnplaced` `.statusWorking` `.statusWaiting` `.statusIdle` | 카드 상태 배지 | 상태 배지 참고 |
| | `.filters` `.searchWrap` `.filterButton` `.filterButtonOn` `.filterReset` | 검색+필터 | 필터바 |
| | `.popup` (`composes: glassStrong`) `.popupLabel` | 필터 팝업 | 필터 상세 |
| `pages/Settings/Settings.module.css` | `.layout` `.menu` `.menuItem` `.menuItemActive` `.menuTitle` `.menuIcon` | 설정 좌측 메뉴 | 설정 화면 |
| | `.content` `.sectionHeader` `.iconButton` | 내용 영역·제목 줄 | 설정 화면 |
| | `.card` (`composes: glass`) `.cardHeader` `.cardTitle` `.editor` | 런타임 카드 | 설정 카드 |
| | `.statusPill` `.statusPillOn` `.statusPillOff` `.statusDot` | on/off 알약 | on/off 표시 |
| | `.withIcon` `.ghostButton` `.dangerIcon` | 아이콘 버튼 | 버튼 |
| | `.themeOptions` `.themeOption` `.themeOptionActive` `.themeIcon` `.themeLabel` | 테마 선택 | 테마 섹션 |

## 3. 부족(신규 필요) 토큰 - 신규 추가 없이 기존 토큰으로 대체

아래는 "이상적으로는 전용 토큰이 있으면 좋지만 **지금은 만들지 않고** 기존 토큰으로 대체"하는 항목이다. `tokens.css` 는 수정하지 않는다. 판단 결과는 **전부 "기존 토큰으로 구현 가능 - 신규 보류"** 다.

| 부족 후보 | 필요한 이유 | 현재 대체안 | 판단 |
| --- | --- | --- | --- |
| `--color-success` / `--color-success-bg` | 완료(SUCCEEDED)를 초록으로 구분 | `--color-primary`(강조) 또는 `--color-muted`(조용히), 면은 `--color-code-bg` | **대체 가능** - 기존 규칙(`execution.done`=muted, `Toaster.success`=primary)과 충돌 방지 위해 신규 보류 |
| `--color-row-hover` | 표/행 hover 전용 면 | `--color-code-bg`(전 화면 hover 관례) | **대체 가능** - 보류 |
| `--color-chip-bg` / `--color-chip-border` | 필터 칩 전용 면 | 경계 `--color-border-strong`, 켜짐 `--color-primary`+`--color-on-primary`, hover `--color-code-bg` | **대체 가능** - 보류 |
| `--color-progress` / `--color-progress-track` | 진행률 바 | 채움 `--color-primary`, 트랙 `--color-code-bg` | **대체 가능** - 보류 |
| `--color-focus-ring` | 포커스 링 색 | `--color-code-bg`(전역 `:focus-visible` 관례) | **대체 가능** - 보류 |
| `--color-skeleton` | 로딩 스켈레톤 | `--color-code-bg` / `--color-surface` | **대체 가능** - 보류 |
| `--radius-sm` (`6px`) | 작은 배지·칩·메뉴 항목 반경 | 리터럴 `6px`(기존 `menu.item`·`execution.md code` 와 동일) | **대체 가능** - 기존 관례가 리터럴이므로 보류 |
| `--spacing-xs`(`4px`) · `--spacing-2xs`(`6px`) · `12px` | 촘촘한 목록 간격 | 리터럴 `2·4·6·12px`(기존 코드 전반) | **대체 가능** - 보류 |
| `--shadow-sm` | 가벼운 그림자 | `--shadow-float` 또는 링 `0 0 0 1px var(--color-primary)` | **대체 가능** - 보류 |
| `--badge-radius` | 알약 반경 | 리터럴 `999px`(기존 배지·칩 전부) | **대체 가능** - 보류 |

> 결론: **신규 토큰 0개.** 두 화면 모두 위 매핑표의 기존 토큰과 공용 클래스만으로 구현 가능하다. 새 색이 정말 필요해지면 라이트·다크·`prefers-color-scheme` 세 곳에 함께 추가하는 규칙을 지키되, 그 전에 이 문서의 "부족 토큰" 표에 먼저 등록하고 판단을 남긴다.

## 레이아웃

### 0. 공통 제약 (모든 화면)

- **왼쪽 아이콘 사이드바는 항상 있다**(`components/Sidebar.tsx`). 내용 영역은 **사이드바를 뺀 전체 폭**을 쓴다 — `max-width` 로 가운데 정렬하지 않는다(`layout.module.css` 의 `.main` 이 이미 `padding: 24px` + `flex: 1`).
- **색은 `apps/frontend/src/styles/tokens.css` 의 기존 토큰만** 쓴다(`var(--...)`). 새 토큰을 만들지 않는다.
- **떠 있는 패널/창은 기존 글래스**를 `composes` 로 재사용한다(`styles/glass.module.css` 의 `glass`/`glassStrong`, `styles/modal.module.css`). 페이지 안에 놓인 큰 목록·카드도 같은 결을 유지한다.
- **글래스(backdrop-filter) 요소에는 `overflow` 를 두지 않는다**(모서리 밖으로 블러가 네모나게 샌다). 내부 스크롤이 필요하면 **블러가 없는 안쪽 요소**(예: `.rows`)에서 자른다.
- CSS 는 전부 파일로 분리하고 **인라인 `style` 금지**. 화면 고유 클래스는 그 화면의 `*.module.css` 에, 공용 조각은 `styles/shared.module.css`.
- 간격은 토큰만: `--spacing-sm`(8) · `--spacing-md`(16) · `--spacing-lg`(24). 반경: `--radius`(10) · `--radius-lg`(16).
- 전역 오버레이(`AppStatus` · `Toaster` · `ConfirmDialog`)는 `App.tsx` 가 한 번 그린다 — 각 화면 레이아웃에서 다시 그리지 않는다.

---

### 1. Tasks / 실행 목록 화면 (`/tasks`)

**폴더 결정**: 현재 `apps/frontend` 는 `pages/` 구조이고 지시대로 **`pages/Tasks/`** 에 둔다.
(`agents/CONVENTIONS.md` 의 "기능 우선(`features/<도메인>/`)" 규칙과 어긋난다 — 팀이 `features/` 로 옮길 때 `features/tasks/` 로 함께 이동한다. 아래 경로는 `pages/Tasks/` 기준.)

#### 1.1 영역 구성도

```
┌ 내용 영역 = 사이드바 제외 전체 폭 (max-width 중앙정렬 없음, padding 24) ───────────┐
│ ┌ Header ───────────────────────────────────────────────────────────────────────┐ │
│ │ Tasks / 실행 목록 (h1)                          [ 새로고침 ]  (오른쪽 액션)    │ │
│ │ 한 줄 설명(hint): 진행·종료 작업/실행을 프로젝트·상태로 걸러 본다              │ │
│ └───────────────────────────────────────────────────────────────────────────────┘ │
│        ↕ --spacing-lg                                                              │
│ ┌ FilterBar ────────────────────────────────────────────────────────────────────┐ │
│ │ [프로젝트 셀렉트]  [전체|진행|완료|실패|대기 …: 상태 칩]   [검색] [정렬]        │ │
│ │  (좁은 화면: 줄바꿈 — 아래 1.5)                                                │ │
│ └───────────────────────────────────────────────────────────────────────────────┘ │
│        ↕ --spacing-md                                                              │
│ ┌ ListPanel (glass) ───────────────────────────────────────────────────────────┐ │
│ │  ListHead: [상태][제목 / 지시][프로젝트][담당 에이전트][지표][시각]            │ │
│ │  ─────────────────────────────────────────────────────────────────────────    │ │
│ │  .rows (내부 스크롤 — 블러 없는 안쪽 요소)                                     │ │
│ │    Row  ▸ [배지] 지시/제목 …   · 프로젝트 · 에이전트 · 비용/토큰 · 시각        │ │
│ │    Row  ▸ …                                                                    │ │
│ │  (loading / empty / error 는 이 영역 안에서 세로·가로 중앙 정렬)               │ │
│ └───────────────────────────────────────────────────────────────────────────────┘ │
│        ↕ --spacing-md                                                              │
│ ┌ Footer ───────────────────────────────────────────────────────────────────────┐ │
│ │ 전체 N건 · 도는 것 M건 (muted)          [ 더 보기 ] 또는 페이지 (오른쪽)       │ │
│ └───────────────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────────┘
```

행을 누르면 **실행 트리 창**(기존 `pages/Projects/ExecutionTree.tsx` 의 `ExecutionTreeModal`, `styles/execution.module.css` 의 `.modal` = `glassStrong`)을 띄운다. 창은 화면 중앙(`position: fixed; inset`)에 오고, 레이아웃은 기존 창을 그대로 재사용한다(새 창을 만들지 않는다).

#### 1.2 영역별 배치 · 간격 · 정렬

| 영역 | 배치 | 간격 | 정렬 |
| --- | --- | --- | --- |
| `.page` (루트) | 세로 `flex` 컬럼 | `gap: var(--spacing-lg)` | `align-items: stretch` (전체 폭) |
| Header | `flex`, `justify-content: space-between`, `align-items: flex-start`, `flex-wrap: wrap` | 제목과 액션 사이 `gap: var(--spacing-md)` | 제목 왼쪽, 액션 오른쪽 |
| `.title` h1 | — | 아래 4px | — |
| `.filterBar` | `flex`, `align-items: center`, `flex-wrap: wrap` | `gap: var(--spacing-sm)`, 그룹 내부 `gap: 6px` | 왼쪽에 필터, 오른쪽(`margin-left:auto`)에 검색·정렬 |
| `.statusChips` | `flex`, `flex-wrap: wrap` | `gap: 6px` | 가로 나열 |
| `.listPanel` | 세로 `flex` 컬럼, `glass` composes, `border-radius: var(--radius-lg)`, `padding: var(--spacing-sm) var(--spacing-sm) 0` | 내부 머리글–행 사이 `border-bottom` 1px | — |
| `.listHead` | CSS grid(열 폭은 1.2 의 열 비율) | 셀 좌우 `padding: 0 10px`, 높이 34px | 왼쪽 정렬, 숫자 열은 오른쪽 정렬 |
| `.rows` | 세로 컬럼, `overflow-y: auto`(여기만 스크롤), `flex: 1`, `min-height: 0` | 행 사이 `gap: 2px` | — |
| `.row` | CSS grid — 머리글과 **같은 `grid-template-columns`** | `padding: 9px 10px` | 세로 가운데(`align-items: center`) |
| Footer | `flex`, `justify-content: space-between`, `align-items: center` | `gap: var(--spacing-md)` | 왼쪽 개수(muted), 오른쪽 페이지/더 보기 |

**목록 열 비율(제안, 머리글=행 동일)**: `minmax(84px, auto) 1.6fr minmax(120px, 0.9fr) minmax(110px, 0.8fr) auto auto`
= `상태 · 제목/지시 · 프로젝트 · 담당 · 지표 · 시각`. 제목 칸은 `overflow: hidden; text-overflow: ellipsis; white-space: nowrap`(한 줄, 툴팁으로 전체).
지표(비용·토큰·시간)는 `font-variant-numeric: tabular-nums` 로 흔들리지 않게 한다.
**정렬 규칙**: 기본 최신순(실행 id 내림차순). 필터바의 정렬 셀렉트가 이 기본값을 덮는다.

#### 1.3 컴포넌트 분해 → 파일 경로

| 컴포넌트 | 파일 경로 | 역할(레이아웃) |
| --- | --- | --- |
| `Tasks` (화면 루트) | `apps/frontend/src/pages/Tasks/Tasks.tsx` | `.page` 틀 — Header + FilterBar + List + Footer 배치 |
| (스타일) | `apps/frontend/src/pages/Tasks/Tasks.module.css` | 아래 클래스 후보 전부 |
| `TaskFilters` | `apps/frontend/src/pages/Tasks/TaskFilters.tsx` | `.filterBar` — 프로젝트 셀렉트·상태 칩·검색·정렬 |
| `TaskList` | `apps/frontend/src/pages/Tasks/TaskList.tsx` | `.listPanel`(glass) + `.listHead` + `.rows` |
| `TaskRow` | `apps/frontend/src/pages/Tasks/TaskRow.tsx` | `.row` 한 줄(grid) — 누르면 실행 트리 창 |
| `StatusBadge` | `apps/frontend/src/pages/Tasks/StatusBadge.tsx` | 상태 배지 — `styles/execution.module.css` 의 `.badge`+tone 클래스를 `composes` |
| `ListState` (선택) | `apps/frontend/src/pages/Tasks/ListState.tsx` | `.empty`/`.loading`/`.error` 중앙 정렬 블록 |
| `ExecutionTreeModal` | `apps/frontend/src/pages/Projects/ExecutionTree.tsx` (기존 재사용) | 행 클릭 시 트리 창 |
| 메뉴 아이콘 | `apps/frontend/src/components/icons.tsx` | `TasksIcon`(체크리스트/목록) 신규 추가 |
| 사이드바 항목 | `apps/frontend/src/components/Sidebar.tsx` | `MENUS` 에 `{ to: '/tasks', label: 'Tasks', Icon: TasksIcon }` 추가(프로젝트 아래) |
| 라우트 | `apps/frontend/src/App.tsx` | `<Route path="/tasks" element={<Tasks />} />` (Layout 안쪽, `/projects` 다음) |

> 화면은 **스토어(`useAgentDockStore`)만** 읽는다(직접 `fetch` 금지). 목록 데이터/필드는 정보구조 담당(실행 228)과 UX 담당(234)의 절을 따른다 — 레이아웃은 "행 하나가 상태 배지 + 제목 + 3~4개 보조 필드"라는 골격만 고정한다.

#### 1.4 CSS 모듈 클래스 후보 (`Tasks.module.css`)

- 틀/헤더: `.page` · `.header` · `.title` · `.subtitle` · `.headerActions`
- 필터바: `.filterBar` · `.filterGroup` · `.filterLabel` · `.statusChips` · `.statusChip` · `.statusChipActive` · `.projectSelect` · `.searchInput` · `.sortSelect`
- 목록: `.listPanel`(composes `glass`) · `.listHead` · `.listHeadCell` · `.listHeadCellNum`(오른쪽 정렬) · `.rows` · `.row` · `.rowSelected` · `.cellStatus` · `.cellTitle` · `.cellTitleText` · `.cellProject` · `.cellAgent` · `.cellMetrics` · `.cellTime`
- 배지: `.badge`(composes `execution.module.css` 의 `badge`), tone 은 공용 `active`/`wait`/`done`/`failed` 를 그대로 `composes` — `lib/executions.ts` 의 `STATUS_LABEL`/`STATUS_TONE` 을 그대로 쓴다.
- 상태: `.stateBlock` · `.stateText` · `.stateRetry`
- 푸터: `.footer` · `.count` · `.pager` · `.loadMore`
- 좁은 화면: `.rowStacked` 안에서 쓸 `.cellMeta` 등

#### 1.5 좁은 화면 대응 (≤ 720px, `@media (max-width: 720px)`)

- **필터바**: 한 줄에 다 안 들어가면 줄바꿈. 프로젝트 셀렉트는 `flex: 1 1 100%`(한 줄 전체), 상태 칩은 `.statusChips { overflow-x: auto; flex-wrap: nowrap; -webkit-overflow-scrolling: touch }` 로 가로 스크롤(칩이 잘리지 않게 `padding-bottom: 2px`). 검색·정렬은 오른쪽 정렬을 풀고 `flex: 1` 로 한 줄에 나눠 담는다.
- **목록**: 머리글(`.listHead`)을 숨기고(`display: none`) 행을 **카드형으로 세로 스택**한다 — `.row { grid-template-columns: auto 1fr; grid-template-areas: 'status title' 'meta meta'; row-gap: 4px }`. 두 번째 줄(`.cellMeta`)에 프로젝트 · 담당 · 지표 · 시각을 `flex-wrap` 으로 나열한다(뮤트 12px). 행 사이 구분은 `border` 유지.
- **푸터**: 개수와 더 보기를 세로로 쌓고(`flex-direction: column; align-items: flex-start; gap: 6px`), 더 보기 버튼은 폭 100% 까지 늘려도 된다.
- **터치 타깃**: 행 최소 높이 44px 이상(좁은 화면에서만 `padding` 을 키운다).
- 표 안에서 셀 폭이 부족하면 제목 칸을 먼저 줄이고(1fr), 상태 배지·지표는 고정 폭으로 지킨다.

#### 1.6 사용 토큰 (레이아웃에 쓰는 것 — 매핑표는 위 '사용 토큰' 절/233)

- 표면·테두리: `.listPanel` → `--glass-bg`/`--glass-border`/`--glass-highlight`/`--shadow-float`(glass 가 자동), 행 hover/선택 → `--color-code-bg`, 행 경계 → `--color-border`, 강조 테두리 → `--color-border-strong`.
- 텍스트: 제목 → `--color-text`, 보조/시각/개수/힌트 → `--color-muted`.
- 상태 배지: `--color-primary`(active) · `--color-warning`(wait) · `--color-muted`(done) · `--color-error`(failed) — `execution.module.css` 가 이미 쓰는 짝.
- 버튼/필터 칩: 기본 버튼은 전역 `--gradient-accent`/`--color-on-primary`, ghost(검색·정렬·더 보기 등) → `transparent` + `--color-border-strong` + hover `--color-code-bg`, 활성 칩 → `--color-code-bg` + `--color-text`(모두 `Settings.module.css` 의 `ghostButton`/`menuItemActive` 와 같은 짝).
- 입력·셀렉트: `--color-input-bg` + `--color-border-strong` + 포커스 `--color-primary`/`--color-code-bg`(전역 `globals.css` 가 이미 처리, 셀렉트 화살표 `--select-arrow`).

---

### 2. 설정 화면 (`/settings`) — 배치만

새 데이터 흐름을 만들지 않는다. **기존 구조(런타임 카드 / CliPanel / 테마 섹션)** 의 배치만 정리한다. 화면은 `Settings.tsx`(틀) → 중첩 라우트(`agents` = `AgentConnectionSettings`, `theme` = `ThemeSettings`)로 그린다.

#### 2.1 영역 구성도

```
┌ 내용 영역 = 사이드바 제외 전체 폭 ────────────────────────────────────────────────┐
│ ┌ Settings.menu (220, sticky) ─┐ ┌ Settings.content (flex: 1, min-width: 0) ──────┐ │
│ │ 설정 (menuTitle, muted 12)   │ │ 섹션 헤더 (.sectionHeader)                      │ │
│ │ ▸ 에이전트 연결 설정 (active)│ │   h1 (에이전트 연결 설정)      [ + 런타임 추가 ] │ │
│ │ ▸ 테마                       │ │ 힌트(.hint): 무엇을 여기서 관리하는지            │ │
│ │                              │ │                                                 │ │
│ │                              │ │ ┌ RuntimeCard (glass) ────────────────────────┐ │ │
│ │                              │ │ │ 카드 머리: 이름 (key)            [런타임 삭제]│ │ │
│ │                              │ │ │ 상태 필 켜짐/꺼짐 + 안내 문구                │ │ │
│ │                              │ │ │ 액션 줄: [끄기] [CLI 확인]                   │ │ │
│ │                              │ │ │ 모델: … / 모드: …           [모델/모드/설치 편집]│ │ │
│ │                              │ │ │ (펼침) .editor 패널 — textarea/input + [저장] │ │ │
│ │                              │ │ └─────────────────────────────────────────────┘ │ │
│ │                              │ │ ┌ RuntimeCard … (세로로 반복) ────────────────┐ │ │
│ │                              │ │ └─────────────────────────────────────────────┘ │ │
│ │                              │ └─────────────────────────────────────────────────┘ │
│ └──────────────────────────────┘ └─────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────┘
  · CliPanel / AddRuntimeModal 은 createPortal 로 body 위에 뜬다(패널 블러·스크롤에 안 잘리게).
  · theme 섹션: h1 + hint + .themeOptions(세로 스택의 glass 옵션 행 3개).
```

#### 2.2 영역별 배치 · 간격 · 정렬

| 영역 | 배치 | 간격 | 정렬 |
| --- | --- | --- | --- |
| `.layout` | `flex`, `align-items: flex-start` | `gap: var(--spacing-lg)` | 메뉴 왼쪽, 내용 오른쪽 |
| `.menu` | 세로 컬럼, `width: 220px; flex-shrink: 0` | 항목 사이 `gap: 4px` | 왼쪽 정렬 |
| `.menu` (신규 배치) | `position: sticky; top: var(--spacing-lg)` — 내용이 길어도 메뉴가 남는다 | — | — |
| `.content` | `flex: 1; min-width: 0` (전체 폭, 중앙정렬 없음) | — | — |
| `.sectionHeader` | `flex`, `justify-content: space-between`, `align-items: center`, `flex-wrap: wrap` | `gap: var(--spacing-md)`, 아래 `margin-bottom: var(--spacing-md)` | 제목 왼쪽, 오른쪽 액션 아이콘 |
| `.cards` (신규, 카드 묶음) | 세로 컬럼 | `gap: var(--spacing-md)` | stretch |
| `.card` (RuntimeCard) | `glass` composes, `border-radius: var(--radius-lg)`, `padding: var(--spacing-lg)` | 카드 내부 블록 사이 `margin-top: var(--spacing-md)` | — |
| `.cardHeader` | `flex`, `justify-content: space-between`, `align-items: center` | `gap: var(--spacing-md)` | 이름 왼쪽, 삭제 오른쪽 |
| `.themeOptions` | 세로 컬럼 | `gap: var(--spacing-sm)`, 위 `margin-top: var(--spacing-md)` | stretch |

> **바꾸지 않는 것**: `RuntimeCard` 내부 순서(카드 머리 → 상태 필 → 액션 → 모델/모드 → 편집 패널), `CliPanel`/`AddRuntimeModal` 의 portal 방식, 테마 옵션 3개(시스템/라이트/다크). 데이터·동작은 손대지 않는다.

#### 2.3 컴포넌트 분해 → 파일 경로

| 컴포넌트 | 파일 경로 | 레이아웃 역할 |
| --- | --- | --- |
| `Settings` (틀) | `apps/frontend/src/pages/Settings/Settings.tsx` | `.layout` = `.menu` + `.content`(Outlet) |
| `AgentConnectionSettings` | `apps/frontend/src/pages/Settings/AgentConnectionSettings.tsx` | `.sectionHeader` + `.hint` + `.cards` |
| `RuntimeCard` | `apps/frontend/src/pages/Settings/RuntimeCard.tsx` | 카드 한 장의 블록 배치 |
| `CliPanel` | `apps/frontend/src/pages/Settings/CliPanel.tsx` (기존 재사용) | portal 창 |
| `AddRuntimeModal` | `apps/frontend/src/pages/Settings/AddRuntimeModal.tsx` (기존 재사용) | portal 창 |
| `CommandPanel` | `apps/frontend/src/pages/Settings/CommandPanel.tsx` (+ `.module.css`) | CLI/로그인/설치 출력 |
| `ThemeSettings` | `apps/frontend/src/pages/Settings/ThemeSettings.tsx` | 테마 옵션 스택 |
| (스타일) | `apps/frontend/src/pages/Settings/Settings.module.css` | 아래 클래스 후보 |
| (선택) 분리 | `apps/frontend/src/pages/Settings/RuntimeCard.module.css` | 카드가 커지면 카드 전용 클래스만 분리 |

#### 2.4 CSS 모듈 클래스 후보 (`Settings.module.css` — 기존 클래스는 유지, 신규만 추가)

- 기존(그대로): `.layout` · `.menu` · `.menuTitle` · `.menuItem` · `.menuItemActive` · `.menuIcon` · `.content` · `.sectionHeader` · `.iconButton` · `.statusPill`(+`On`/`Off`) · `.statusDot` · `.withIcon` · `.ghostButton` · `.dangerIcon` · `.themeOptions` · `.themeOption`(+`Active`) · `.themeIcon` · `.themeLabel` · `.card` · `.cardHeader` · `.cardTitle` · `.editor`
- 신규 후보: `.menuSticky`(sticky 래퍼 — `.menu` 에 직접 줘도 됨) · `.cards`(카드 세로 스택 `gap`) · `.cardActions`(액션 줄 `flex-wrap`) · `.cardMeta`(모델/모드 요약 줄) · `.fieldLabel`(편집 패널 라벨)
- 좁은 화면용: `.menu` 를 가로 탭으로 바꿀 때 `.menuTabs` · `.menuItemTab`

> `.card` 의 `margin-top`(현재) 은 `.cards { gap }` 로 흡수하는 편이 낫다(첫 카드만 여백이 붙던 문제 제거). 다만 이는 선택 사항 — 최소 변경으로 가려면 `margin-top` 을 유지해도 된다.

#### 2.5 좁은 화면 대응 (≤ 820px, `@media (max-width: 820px)`)

- **`.layout`**: `flex-direction: column`. 안쪽 왼쪽 메뉴를 **가로 탭 줄**로 바꾼다 — `.menu { width: 100%; flex-direction: row; overflow-x: auto; border-right: none; border-bottom: 1px solid var(--color-border); padding-right: 0; padding-bottom: var(--spacing-sm) }`. `.menuTitle` 은 숨긴다(`display: none`). 항목은 `white-space: nowrap` 으로 가로 스크롤(여전히 sticky top 유지 가능).
- **`.content`**: 전체 폭 유지, 카드 내부 편집 패널(`.editor`)의 textarea 는 `width: 100%`.
- **`.sectionHeader`**: 제목과 액션이 한 줄에 안 들어가면 wrap. 액션 아이콘 버튼은 36px 유지(터치 타깃).
- **`.cards`**: 카드 한 장이 폭 전체. 2열 그리드로 바꾸지 않는다(카드 안에 편집 패널이 펼쳐져 세로가 길다).
- `CliPanel`/`AddRuntimeModal` 창은 기존 반응형(`min(...)`/`calc(100vw - 32px)`)을 그대로 쓴다 — 여기서 새로 손대지 않는다.

---

### 3. 배선(레이아웃 수준)

- 신규 라우트 1개: `apps/frontend/src/App.tsx` 의 Layout 안쪽에 `/tasks`.
- 사이드바 항목 1개: `apps/frontend/src/components/Sidebar.tsx` `MENUS` + `apps/frontend/src/components/icons.tsx` 에 `TasksIcon`.
- 설정 화면 라우트/중첩 라우트(`/settings`, `/settings/agents`, `/settings/theme`)는 **그대로** — 새 섹션을 추가할 때만 `Settings.tsx` 의 `SECTIONS` 와 `App.tsx` 중첩 라우트에 함께 추가한다.

### 4. 남은 결정(다른 담당과 맞출 것)

1. **폴더 규칙**: 지시대로 `pages/Tasks/` 로 두되, `agents/CONVENTIONS.md` 의 기능 우선(`features/<도메인>/`) 규칙과 어긋난다. 팀이 `features/` 로 옮기면 `features/tasks/` 로 함께 이동(정보구조/아키텍처 담당과 합의).
2. **목록 단위**: 행 하나를 **실행(트리 루트) 단위**로 둘지 **Task 단위**로 둘지는 정보구조 담당(실행 228)이 못 박는다. 이 레이아웃은 두 경우 모두 "배지 + 제목 + 보조 필드 3~4개" 행 골격으로 성립한다.
3. **열 구성 확정**: 위 열 비율은 제안이다 — 표시 필드가 확정되면 UX 담당(234)과 칸 수를 맞춘다.

---

## 상태별 표현

> 담당: UX(234). 색·클래스는 `사용 토큰`(233)의 매핑만 쓴다 — **신규 토큰 0개**. 화면 문구는 `apps/frontend/src/lib/executions.ts` 의 `STATUS_LABEL` 을 그대로 쓰고, 배지 뼈대는 `styles/execution.module.css` 의 `.badge`+톤 클래스를 `composes` 한다.

### 1. 실행 상태 6종 매트릭스 (목록·필터)

배지 = **점(아이콘) + 문구**. 점은 `currentColor` 를 물려받아 색 토큰을 새로 만들지 않는다(기존 `Settings.statusDot` 과 같은 방식 — 점 지름 7px, `box-shadow` 로 옅은 고리). 앱 안에서 같은 상태는 어디서나 같은 문구·같은 색으로 나타난다.

| 서버 status (프론트 타입) | 화면 문구 (`STATUS_LABEL`) | 배지 톤 클래스 (`STATUS_TONE`) | 색 계열(토큰) | 점 모양(아이콘) | 목록 행에서 | 필터 칩 |
| --- | --- | --- | --- | --- | --- | --- |
| `PENDING` (`QUEUED`) | 대기 | `done` | `--color-muted` | 빈 점 ○ (테두리만) | 배지(중립 · 테두리형) | `[대기]` |
| `RUNNING` | 실행 중 | `active` | `--color-primary` | 채운 점 ● + **맥동**(1.4s, reduced-motion 이면 정지) | 배지(강조) + 지표 `집계 중` | `[실행 중]` |
| `WAITING_CHILD` | 하위 대기 | `wait` | `--color-warning` | 채운 점 ● (정지) | 배지(경고) + `하위 작업 대기` 한 줄 | `[하위 대기]` |
| `SUCCEEDED` (`DONE`) | 완료 | `done` | `--color-muted` | 채운 점 ● (조용히) | 배지(중립 · 테두리형) | `[완료]` |
| `FAILED` | 실패 | `failed` | `--color-error` | 채운 점 ● | 배지(실패) + 사유 툴팁 | `[실패]` |
| `CANCELLED` | 취소 | `done` | `--color-muted` | 빈 점 ○ (옅게) | 배지(중립 · 테두리형) | `[취소]` |

- **한 가지 색 결정(필수)**: 현재 `lib/executions.ts` 의 `STATUS_TONE.QUEUED` 는 `'wait'`(주황)라 **`대기` 와 `하위 대기` 가 같은 색**이 된다. Tasks 목록에서는 이 둘을 구분해야 하므로 `QUEUED` 를 `'done'`(중립 muted)으로 내린다 — 대기는 아직 아무 일도 안 하는 상태라 경고색이 어울리지 않고, 색만으로 구분되지 않는 경우(점 모양 ○ vs ●)까지 문구가 보완한다. 이 한 줄은 `lib/executions.ts` 한 곳만 고치면 채팅 카드·실행 트리 창에도 함께 적용된다(화면마다 색이 갈라지지 않게).
- **참고(목록에 함께 뜨는 상태)**: `WAITING_INPUT`(`입력 대기`, `wait` 톤 + 면 `--color-warning-bg`)과 트리 루트의 `MANUAL`(`수동 병합 필요`, `wait` 톤)도 실행 목록에 나타난다. 둘 다 **요청 창(`RequestsPanel`)과 같은 데이터(실행 목록 API)** 를 쓰며, 배지 왼쪽 띠(`inset 3px 0 0`)로 행에서도 눈에 띄게 한다.
- **색만으로 구분하지 않는다**: 문구 + 점 모양(채움/빔) + (실패) 툴팁을 함께 쓴다(색각 이상 대응). 점은 `aria-hidden`, 문구가 그대로 읽힌다.

### 2. 상태 × 화면 — 로딩 · 빈 목록 · 요청 실패 · SSE 끊김

한 곳(`AppStatus`)에서 전역 통신 상태를 다루고, 목록 영역의 로딩/빈/오류는 목록 안(`.stateBlock`)에서 그린다. **배너를 중복해서 띄우지 않는다.**

| 상황 | 트리거(스토어) | 화면 표현 | 재시도 · 되돌리기 |
| --- | --- | --- | --- |
| 첫 전체 로드 | `loading === true` | 상단 `AppStatus`(`.loading`, `role="status"`) "서버에서 불러오는 중…" + 목록 영역 `.stateBlock` "불러오는 중…"(`aria-busy="true"`) | 자동(로드가 끝나면 사라짐) |
| 조각 재조회(필터 변경·이벤트) | 진행 중 | **이전 목록 값을 그대로 두고** 위에 얇은 진행 표시만(빈 화면으로 깜빡이지 않게). 목록은 이벤트 250ms 디바운스로 통째 교체 | 자동 |
| 빈 목록(필터 없음) | `executionList.length === 0 && !loading && error === null` | `.stateBlock` "아직 실행이 없습니다 — 프로젝트에서 명령을 보내면 여기에 쌓입니다."(`.hint`) | 헤더 [새로고침] |
| 빈 목록(필터 있음) | 위 + 활성 필터 있음 | "조건에 맞는 실행이 없습니다." + `[필터 초기화]` 버튼 | 초기화 → URL 쿼리 제거 → 전체 목록 |
| 요청 실패(전역) | 스토어 `error !== null` | `AppStatus`(`.error`, `role="alert"`) 사유 + `[다시 시도]` | [다시 시도] → `reload()` |
| 목록 조각 실패 | `fetchExecutionList` 의 catch | **마지막 성공 목록 유지**(조각별 실패를 견딘다 — 전역 오류로 승격하지 않음, 콘솔만) | 안전망 폴링(30초)/이벤트가 다음에 갱신 |
| 목록 첫 조회 실패 | 첫 로드 실패 + 목록 빔 | `.stateBlock` "목록을 불러오지 못했습니다." + `[다시 시도]` | [다시 시도] |
| SSE 끊김 | `streamConnected === false`(로딩·오류 아닐 때) | `AppStatus`(`.offline`, `role="status"`) "실시간 연결이 끊겨 있습니다 — 다시 연결하는 동안 주기적으로 다시 읽습니다." | 자동 재연결(EventSource) + `[다시 시도]` → `reload()` |

- `AppStatus` 는 **한 번에 하나만** 그린다(`loading` → `error` → `offline` 순, 기존 코드와 동일). Tasks 화면은 자기 로딩/빈/오류를 `.stateBlock` 으로 표현할 뿐, 별도 전역 배너를 추가하지 않는다.
- 실시간 갱신은 `execution.changed` → **같은 필터로 실행 목록만 재조회**(계약 §7)를 따르고, 목록은 부분 병합(`mergeExecutions`) 없이 응답 배열로 통째 교체한다.
- 정렬은 **기본 최신순(id desc)** 이고, "더 보기" 는 `beforeId` 커서(받은 개수 === `limit` 이면 더 있음)로 이어 붙인다. 로딩 중 "더 보기"는 버튼을 `disabled`.

---

## 인터랙션

각 항목은 **트리거 → 결과 → 되돌리기** 형태로 적는다. 되돌리기 어려운 동작(취소·정리·삭제)만 확인 창을 쓴다.

### I1. 필터 · 정렬 · 검색 (URL/상태 유지)

- **트리거**: 프로젝트 셀렉트 변경 / 상태 칩 토글 / 검색어 입력 / 정렬 셀렉트 변경.
- **결과**: URL 쿼리스트링에 반영한다(`/tasks?project=3&status=RUNNING,WAITING_CHILD&q=로그인&sort=-id`). 화면은 URL 을 **진실로** 삼아(React Router `useSearchParams`) 값을 읽어 `loadExecutionList({ projectId, status })` 를 부르고, 응답으로 `executionList` slice 를 교체한다. 검색은 서버 계약에 `q` 가 없으므로 **클라이언트 필터**(제목·지시·에이전트 이름, 250ms 디바운스), 상태·프로젝트는 **서버 필터**다.
- **표시 결합**: 상태 칩은 다중 선택 **OR**(합집합), 프로젝트·검색과는 **AND**. 켜진 칩은 `AgentList.filterButtonOn`(채움), 꺼진 칩은 `filterButton`(글자 muted).
- **되돌리기**: `[전체]` 칩(상태 해제), `[필터 초기화]`(모든 쿼리 제거), 브라우저 **뒤로 가기**(URL 이 진실이라 이전 필터 조합이 그대로 복원). 새로고침·링크 공유 시에도 URL 로 같은 화면이 재현된다.

### I2. 행 클릭 → 실행 트리 창 · 터미널 전환

- **트리거**: 목록 **행 클릭**(또는 행에 포커스 후 `Enter`/`Space`).
- **결과**: 기존 `ExecutionTreeModal`(`pages/Projects/ExecutionTree.tsx`, `exec.module: .modal` = `glassStrong`)을 그대로 재사용해 연다. 행이 **자식 실행**이면 `parentExecutionId` 를 따라 올라가 그 트리의 **루트**를 찾아 `rootExecutionId` 로 넘긴다(자식 하나만 보여 주지 않는다). 동시에 스토어 `watchExecutionTree(projectId, rootId)` 로 그 트리를 계속 다시 읽게 한다. 창은 `createPortal` 로 body 위에 뜨고, **URL 은 바꾸지 않는다**(오버레이라 뒤로 가기로 닫히지 않게).
- **터미널 전환**: 목록 행 오른쪽 `⋮` 메뉴 또는 트리 창의 각 행에서 **[터미널 보기]** → 그 실행의 **담당 에이전트 터미널**을 새 창으로 연다(기존 `/terminal/:agentId` 라우트, 메뉴 클릭 핸들러 안에서 `window.open` — 팝업 차단 회피). 실제 구현에서는 새 창이 그 실행의 로그 스트림(`GET /executions/{id}/stream`) 또는 다중화 `GET /executions/stream?ids=` 를 직접 구독하고, 지금의 `BroadcastChannel` 다리는 필요 없다.
- **되돌리기**: 트리 창은 **닫기 버튼 / `Esc` / 오버레이 클릭** → `watchExecutionTree(projectId, null)`. 터미널은 **새 창을 닫으면** 끝(메인 창 상태는 변하지 않는다). 행의 보조 버튼(⋮·터미널)은 `stopPropagation` 으로 행 클릭과 겹치지 않게 한다.

### I3. 되돌리기 어려운 동작의 확인 창(ConfirmDialog)

**규칙**: 되돌리기 쉬운 동작(hover, 필터, 창 열기/닫기, 토스트 닫기)에는 확인 창을 **쓰지 않는다**. 취소·정리·삭제처럼 되돌릴 수 없는 것만 `askConfirm()`(`components/ConfirmDialog.tsx`, `role="alertdialog"`)을 쓴다. 문구 형식은 **대상 + 영향 범위 + 되돌릴 수 없음 안내**이고, `confirmLabel` 은 동작 이름(중지/정리/삭제), 기본 포커스는 **안전한 쪽(취소)**, `Esc` = 취소다.

| 동작 | 트리거 | 확인 문구(요지) | 결과 | 되돌리기 |
| --- | --- | --- | --- | --- |
| **실행 취소** | 행/트리 행의 [취소] (`isLive` 4종에만 노출) | "실행 #N 을(를) 중지할까요? **위임된 자식 실행도 함께 멈춥니다.**" | 버튼 `disabled` + "취소 중…" → 취소 요청 → **성공/실패 모두** `reload()` 로 실제 상태 반영 | **없다** — 되돌릴 수 없어 확인 창을 쓴다 |
| **워크트리 정리** | 트리 창 **루트 행**의 [워크트리 정리] | "워크트리와 전용 브랜치를 지울까요?\n<경로>" | `removeWorktree(id, true)` → 경로·브랜치 비움 | **없다**(브랜치까지 지우면 복구 불가) — 그래서 경로·브랜치를 문구에 명시 |
| **런타임 삭제** | 설정 카드의 [런타임 삭제] | "이 런타임을 쓰는 에이전트는 사용 불가가 되며 다른 런타임을 다시 할당해야 합니다." | `deleteProvider(id)` | **없다** |

- **취소 문구 통일 주의(실측 불일치)**: 지금 `ExecutionTree.tsx` 의 `CancelButton` 은 "지금 도는 **이 실행만** 멈춥니다"라고 하고, `RequestsPanel` 의 중지는 "위임된 자식도 함께"라고 한다. `CONVENTIONS.md` 의 **"취소는 자손까지"** 가 기준이므로 두 곳 모두 **자손까지 멈춘다**는 문구로 통일한다.
- 서버가 이미 끝났거나 도는 중이 아니면 취소는 404 를 오류로 만들지 않는다(`api.cancelExecution`). 워크트리 정리는 도는 실행이면 **409** — 버튼을 누르기 전에 `isLive` 로 막고, 409 면 사유를 토스트로 알린다.

### I4. 완료 · 실패 · 취소 토스트(Toaster) — '종류별 한 번'

전역 스토어의 상태 전이 감시가 **한 곳에서만** 띄운다(Tasks 화면이 따로 중복해서 띄우지 않는다). `Toaster.toast(text, tone)` 는 화면 **상단 가운데**, 최대 4개, 클릭으로 닫힘.

| 종류 | 트리거(전이) | 톤 | 문구(예) | 횟수 |
| --- | --- | --- | --- | --- |
| 시작 | `QUEUED → RUNNING` **일 때만** | `info` | `#12 Backend Dev 작업을 시작했습니다` | 종류별 **1회** |
| 하위 대기 | `→ WAITING_CHILD` | `info` | `#12 … 하위 작업을 기다립니다` | 종류별 **1회** |
| 완료 | `→ DONE` | `success` | `#12 … 작업 완료 (1분 20초)` | 종류별 **1회** |
| 실패 | `→ FAILED` | `error` | `#12 … 작업이 실패했습니다` | 종류별 **1회** |
| 취소 | `→ CANCELLED` | `info` | `#12 … 작업이 취소되었습니다` | 종류별 **1회** |
| 입력 대기 | `→ WAITING_INPUT` | `warn` | `#12 … 답을 기다립니다 — 요청 창에서 답해 주세요` | **매번** |

- **재개를 시작으로 알리지 않는다**: `WAITING_CHILD/WAITING_INPUT → RUNNING` 재개는 "시작"이 아니다(새로고침할 때마다 돌고 있는 작업이 '시작'으로 다시 뜨는 것을 막는다).
- **중복 방지**: `notifiedRef` 키(`start:<id>`·`done:<id>` …)로 이미 알린 것은 다시 띄우지 않는다. Tasks 화면에서 사용자가 취소를 눌러 `CANCELLED` 가 되어도 토스트는 **1회**만 뜬다.
- 되돌리기: 알림은 정보라 되돌리기가 없다 — 클릭으로 즉시 닫거나 `TOAST_MS`(info/success 4초, warn 6초, error 7초) 뒤 사라진다.

### I5. 설정 화면 상호작용 (런타임 이름 · on/off · CLI 상태 · capabilities · 추가)

기존 구조(런타임 카드 / `CliPanel` / `AddRuntimeModal`)의 **동작 규칙**만 정한다. 저장은 기존 스토어 `call()` 패턴(성공 시 `reload()`, 실패 시 전역 오류)을 그대로 따른다.

| 기능 | 트리거 | 결과 | 되돌리기 |
| --- | --- | --- | --- |
| **런타임 이름 편집** | 카드 제목(`.cardTitle`)의 편집 아이콘 → 인라인 `input` | `Enter`/포커스 아웃으로 저장 → `updateProvider(id, {name})` → `reload()` → 제목 갱신 | `Esc` 또는 빈 값 → 원래 이름 복원(저장 안 함) |
| **on/off 토글** | [켜기]/[끄기] 버튼 | `toggleProvider(id)` → `reload()` → 상태 필 `.statusPillOn`/`Off`(`켜짐`/`꺼짐`) 갱신 | **다시 눌러 원복**(비파괴 — 확인 창 없음) |
| **CLI 상태 일괄 표시** | `agents` 섹션 진입 | 카드마다 `GET /ai-providers/{id}/cli` 를 **병렬로 한 번** 조회해 카드에 "경로 · 버전 · 실행 가능/불가" 한 줄 표시(저장 안 함). 조회 실패는 "CLI 정보를 확인할 수 없습니다"(카드별로 독립) | 읽기 전용(부작용 없음) — [CLI 확인](`CliPanel`)에서 상세·probe·설치·로그인 |
| **capabilities 편집** | [모델/모드/설치 편집] → 편집 패널 | 로컬 **초안**(models/modes/install/… textarea)을 [저장] 시 정규화(trim·빈값·중복 제거) 후 `updateCapabilities(id, {...})` → 성공 토스트 1회 | [설정 닫기] = 초안 폐기(저장 안 함). 저장 후에는 다시 열어 고친다(서버 값이 기준) |
| **새 런타임 추가** | 헤더 `+` → `AddRuntimeModal` | 키 선택 → 표시 이름 → [추가] `addProvider(key, name)` → **꺼진 상태로** 추가 | 모달 [취소]/오버레이 = 아무 것도 만들지 않음. 추가 뒤 되돌리려면 카드에서 [런타임 삭제](확인 창) |

- **저장/되돌리기 공통**: 성공하면 `success` 토스트 1회, 실패하면 `AppStatus` 전역 오류 + 이전 값 유지. 편집 패널은 파괴적이지 않으므로 닫을 때 확인 창을 쓰지 않는다(대신 초안이 버려진다는 것을 `.hint` 로 알린다).
- **필요 API(계약 밖, 신규 1건)**: 런타임 **이름 편집**은 현재 스토어/API 에 없다 — `PATCH /ai-providers/{id}`(name) 또는 `updateProvider` 스토어 액션이 필요하다. 나머지(on/off·capabilities·추가·삭제)는 기존 액션을 그대로 쓴다.

### I6. 접근성 메모 (키보드 포커스 · aria · 포커스 링)

- **포커스 링**: 전역 `:focus-visible` 을 그대로 쓴다(없으면 목록 행에 `box-shadow: 0 0 0 1px var(--color-primary), 0 0 0 3px var(--color-code-bg)` — 기존 선택 링 규칙). 마우스 클릭에는 링을 띄우지 않는다.
- **목록 행**: `role="button"`(또는 실제 `<button>`) + `tabIndex=0`, `Enter`/`Space` 로 트리 창 열기. `aria-label` 에 **상태 + 제목 + 프로젝트**를 담는다(예: "실패 · 로그인 오류 수정 · 프로젝트 A"). 행 안 보조 버튼은 행 다음 탭 순서.
- **상태 배지**: 점(아이콘)은 `aria-hidden`, 상태는 문구가 그대로 읽힌다. 색만으로 뜻을 전하지 않는다(I1 참고).
- **트리 창**: `role="dialog"`, `aria-modal="true"`, `aria-label="실행 트리 #<id>"`. 열릴 때 포커스를 창 안(닫기 버튼/제목)으로 옮기고, 닫을 때 **트리거였던 행으로 되돌린다**. 창 안에서 포커스가 순환(트랩)하고, 열려 있는 동안 배경 스크롤을 잠근다. `Esc` 로 닫기.
- **확인 창**: `ConfirmDialog` 의 `role="alertdialog"` 를 유지하고, 열릴 때 초기 포커스를 **취소(안전) 버튼**에 둔다.
- **필터 칩**: 실제 `<button>` + `aria-pressed`. 상태 칩 묶음은 `role="group" aria-label="상태 필터"`, 프로젝트 셀렉트·검색은 `<label>`(또는 `aria-label`). 검색은 `type="search"`.
- **실시간 상태**: `AppStatus` 의 `role="status"`(로딩·끊김) / `role="alert"`(오류)와 `Toaster` 의 `aria-live="polite"` 를 그대로 쓴다. 로딩 영역은 `aria-busy="true"`.
- **모션**: `RUNNING` 점의 맥동은 `@media (prefers-reduced-motion: reduce)` 에서 끈다. 그 밖의 전환은 기존 `0.12s ease` 수준을 넘지 않는다.

