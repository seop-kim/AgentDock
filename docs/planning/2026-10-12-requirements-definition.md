# 요구사항 정의서 — 후보 A~H + 소규모 (실행 #242 · 2026-10-12)

이 문서는 실행 #242 이어가기(사용자 지시: 후보 전부 구현)의 **요구사항 정의서(통합 인덱스)** 다. 후보 전체의 **구현 상태표**, **남은 것 상세**, **우선순위·의존관계**, 사람이 결정할 **결정 필요 지점**을 한곳에 모은다. 세부 화면 흐름·데이터 모델·API 계약은 아래 참조 문서가 소관이며, 이 문서는 **중복 서술하지 않고 참조로 잇는다**.

## 0. 참조 문서 지도

| 문서 | 소관 |
| --- | --- |
| `docs/planning/2026-10-12-flows-and-decisions.md` | 미구현 후보의 **화면 흐름 단계** + **결정 지점(선택지 A·B / 영향 / 기본 제안)** + 기존 화면 연결 지도 |
| `docs/planning/2026-10-12-diff-schedules-merge-policy-contract.md` | **C·D·E** 확정 계약(데이터 모델 · Flyway 초안 · API · 이벤트 · 엔티티 변경점) |
| `docs/planning/2026-10-12-cost-budget-notification-contract.md` | **G·H** 확정 계약 |
| `docs/planning/2026-10-08-execution-list-api-contract.md` | A 실행 목록 API(완료분) |
| `docs/planning/2026-10-08-execution-log-multiplex-stream-contract.md` | S2 실행 로그 다중 스트림(완료분) |
| `docs/planning/2026-10-08-frontend-data-wiring.md` · `2026-10-08-tasks-settings-design-spec.md` | 프론트 데이터 배선 규칙 · Tasks/설정 화면 스펙 |
| `agents/CONVENTIONS.md` | 실시간 이벤트 우선 갱신 · 워크트리 격리·자동 병합 · 자격증명 미저장 등 규칙 |

- **후보 ID**: A~H + 소규모는 `.agentdock/prompts/243~254.md` 및 실행 238(요구사항 담당) 목록을 그대로 따른다. 새 ID를 만들지 않는다.
- **첨부 유실**: 이어가기-241 첨부(`.agentdock/attachments/a6a7632d-이어가기-241.md`)는 유실 — 후보 목록은 위 프롬프트 파일들에서 복원했다.
## 1. 후보 전체 상태표

| ID | 후보 | 지시(246) 기준 | 코드 현황(2026-10-12 작업 트리) |
| --- | --- | --- | --- |
| A | 실행 목록 · Tasks 화면 | 완료 | **완료**(머지) — `GET /executions`(ExecutionSummaryResponse), `pages/Tasks` |
| B | 요청 조회(projectId+status) | 완료 | **완료**(머지) — 요청 인박스/대기 조회 |
| C | 변경사항 diff | 남음 | **구현됨(미커밋)** — BE `execution/service/ExecutionDiffService` · `dto/ExecutionDiffResponse`, FE `pages/Projects/ExecutionDiffModal.tsx` |
| D | 반복·예약 실행(`/schedules`) | 남음 | **구현됨(미커밋)** — BE `schedule/` 도메인 + `V23__schedule.sql`, FE `pages/Schedules` |
| E | 병합 정책(`/settings/merge-policy`) | 남음 | **부분** — FE `pages/Settings/MergePolicySettings.tsx` 만 존재, **백엔드(API·테이블) 없음** |
| F | 런타임 설정 3종 | 완료 | **완료**(머지) — `PUT /ai-providers/{id}` · `GET /ai-providers/cli` · `/catalog` |
| G | 비용·예산 | 남음 | **구현됨(미커밋)** — BE `budget/` + `V24__project_budget.sql` · `ExecutionUsagePoint`, FE `pages/Costs` |
| H | 알림 채널(웹훅/Slack/메일) | 남음 | **구현됨(미커밋)** — BE `notification/` + `V25`·`V26`, FE `pages/Notifications` |
| S1 | 채팅 첨부 복원 | 완료 | **완료**(머지) — `TaskResponse.attachments` |
| S2 | 실행 로그 다중화 | 완료 | **완료**(머지) — 리더 탭 단일 SSE(`lib/liveChannel.ts`) |
| S3 | 프로젝트/조직 템플릿 복제 | 남음 | **미구현** — 계약 · API · 화면 모두 없음 |
| S4 | 계약 finish | 남음 | **미구현** — `DelegationContract` 는 delegate/ask/done 만(finish 없음) |

> **"코드 현황"은 작성 시점 스냅샷**이다. C/D/E/G/H 구현은 이번 실행과 **동시에** 진행 중이라 수시로 바뀐다 — 커밋·검증 완료 여부는 검증 팀 판정을 따른다. 남은 것은 사실상 **E 백엔드 · S3 템플릿 복제 · S4 계약 finish** 셋이다.
## 2. 남은 것 상세

각 항목의 **목적 / 화면 흐름 / 필요 API** 는 참조 문서를 따른다(여기서는 요약만).

| ID | 목적 | 흐름(참조) | 필요 API(참조) |
| --- | --- | --- | --- |
| C | 트리 루트가 바꾼 파일의 **파일별 unified diff** 를 창으로 본다 | flows §2 | 계약 §1 — `GET /executions/{id}/diff?path=` (git `ProcessService`, workers 풀) |
| D | 프로젝트 명령을 **주기로 반복**한다(즉시 실행은 기존 명령 재사용) | flows §3 | 계약 §2 — `GET/POST /schedules` · `GET/PUT/DELETE /schedules/{id}` · `POST /schedules/{id}/run` · `schedule.changed` |
| E | 트리 종료 후 **자동 병합·정리 범위**를 전역 정책으로 정한다 | flows §4 | 계약 §3 — `GET/PUT /settings/merge-policy` · `settings.changed` |
| G | **기간별 사용량**(실측 계측값만)과 **프로젝트 월 예산**, 80% 경고 | flows §5 | 계약 §3.1 — `GET /executions/usage` · `GET/PUT/DELETE /projects/{id}/budget` · `budget.changed` |
| H | **웹훅/Slack/메일 채널** + "사건→채널" 규칙 + 테스트 발송(비밀값은 `secret_ref`) | flows §6 | 계약 §3.2 — `/notifications/channels`·`/rules`·`/test`·`/deliveries` · `notification.changed` |
| S3 | 기존 프로젝트(조직) 구성을 새 프로젝트로 **복제** | flows §7(제안) | **계약 없음** → 구현 전 별도 계약서 필요 |
| S4 | 자식 결과 도착 시 **재판단을 생략하고 마무리**(판단 라운드 1회 제거) | 화면 무관 | `DelegationContract` 에 `finish` 추가 + `ContractSchemas` + `ChildOutcome` 성공 판정 |
## 3. 우선순위 · 의존관계

**의존 그래프** (A → B = "B 를 하려면 A 가 먼저/전제"):

- 기존 자산 → C/D/E/G: C ← 트리 결과(`changed_files`, V14) · D ← `POST /projects/{id}/commands` · E ← `recordTreeResult`·`WorktreeService.remove` · G ← `execution` 계측 컬럼(input/output_tokens·cost_usd)
- H → D-4 · G-4: **H 의 이벤트 타입/발송이 먼저 있어야** D 의 예약 실패·G 의 예산 경고를 사람에게 알린다
- D · H → S3: 템플릿 복제가 예약·알림 규칙까지 포함하려면 D·H 가 먼저
- C · S4: **독립**(다른 후보에 의존하지 않음)

**착수 순서 제안**(위험 낮고 가치 큰 것 먼저 · 신규 마이그레이션 번호 오름차순):

1. **C diff** — 신규 마이그레이션 0, 결과 관측 가치 즉시
2. **E 병합 정책** — 정책 저장 + 종료 훅 1개 (단 **백엔드부터** 신규)
3. **D 예약** — `V23`, 기존 명령 재사용 (D-4 는 H 이후)
4. **G 비용·예산** — `V24`, 기존 계측 재사용 (G-4 는 H 이후)
5. **H 알림 채널** — `V25`·`V26`, D-4·G-4 의 전제
6. **S4 계약 finish** — 독립 · 소규모, 아무 때나
7. **S3 템플릿 복제** — **계약 선행**, 마지막

> 이미 1~5 의 구현이 작업 트리에 들어가 있고(위 상태표), **남은 우선순위 상위는 E 백엔드 · S4 · S3** 다.
## 4. 결정 필요 지점 (사람 결정)

선택지·영향·기본 제안의 전문은 `flows-and-decisions.md` §2~§8. **팀 결정으로 확정해야 하는 갈림**(추측 금지):

| 결정 | 기본 제안 | 갈릴 때 영향(범위) |
| --- | --- | --- |
| **G-2** 예산 초과 시 동작 | **경고만**(차단 없음) | 차단을 택하면 `start`/`command` 진입 예산 가드 + 트리 예산 관계 + 도는 실행 처리까지 범위 급증 |
| **G-3** 승인 흐름 | **없음** | G-2 차단을 택할 때만 성립(인박스 kind + 승인 API + 기록) |
| **H-1** 알림 설정 위치 | **설정 안쪽 SECTIONS** | 최상위 `/notifications` 를 택하면 화면 담당(18) 전제(`프론트화면18.md`)와 합의 필요 |
| **H-2** 규칙 우선순위 | **전역·프로젝트 둘 다 발송** | 프로젝트 재정의를 택하면 dispatch 식 변경 |
| **D-2** 놓친 실행 처리 | **SKIP**(RUN_ONCE 선택 가능) | RUN_ONCE 기본이면 밀린 작업이 원치 않게 실행될 수 있음 |
| **D-4** 예약 실패 알림 | **알림으로**(`SCHEDULE_FAILED`) | 택하면 H 계약에 이벤트 타입 추가(D·H 합의) |
| **E-1/E-2** 자동 병합·정리 범위 | 병합 **ON** + 워크트리만 정리(브랜치 수동) | 브랜치까지 정리하면 되돌리기 어려움 |
| **T-1/T-2** 복제 단위·항목 | **프로젝트 단위 · 구성만**(자격증명 제외) | 조직 단위·예약/알림 포함이면 S3 계약 범위가 급증 |
## 5. 정합성 경고 (구현·머지 전 확인)

1. **E 백엔드 Flyway 번호 충돌**: 계약은 C/D/E=V23~V25, G/H=V26~V28 로 잡았으나 실제 마이그레이션은 D=`V23` · G=`V24` · H=`V25`·`V26` 이다. **E 백엔드가 들어갈 번호가 비어 있지 않다**(다음 자유 번호 V27) — E 착수 시 번호를 다시 정한다.
2. **D-1 cadence vs cron**: FE `Schedule.cadence`(HOURLY/DAILY/WEEKLY/CRON)는 BE 계약(`cron_expression`+`timezone`)에 없다 → cadence 는 **화면 전용**, 저장은 cron. `ScheduleRequest` 에 넣으려면 계약을 함께 고친다.
3. **C-2/C-3 diff 표시**: `ExecutionDiffModal`·`프론트데이터19.md` 는 diff 를 **전체 한 번에 + `execution.changed` 재조회** 로 배선 — 계약 §1.6/§1.8 기본 제안(**파일 하나씩 · 재조회 안 함**)과 다르다 → 계약에 맞춘다.
4. **G API 경로**: FE `/costs/usage`·`/costs/budgets` vs 계약 `GET /executions/usage`·`/projects/{id}/budget` → **계약 경로**가 기준.
5. **H 화면 자리**: `프론트화면18.md` 는 `/notifications` 최상위 가정, 기본 제안은 **설정 안쪽**(H-1) → 먼저 합의.
6. **D-4 `SCHEDULE_FAILED`**: H 계약 `NotificationEventType` 에 아직 없다 → D·H 계약을 함께 고쳐 확정한 뒤 구현.

## 6. 유실 · 미해결 기록

- **요구사항 담당(24)의 후보 상태표 문서(`2026-10-12-candidate-status.md`)는 실행 실패로 생성되지 않았다** — 이 문서가 그 자리(상태표 + 남은 것 상세 + 우선순위·의존관계)를 대체한다.
- 결정 필요 지점(§4)은 아직 **사람/팀 확정 전**이다. 확정되면 계약·프론트 문서에 반영하고 §5 어긋남을 함께 없앤다.
- S3 템플릿 복제는 **계약서가 먼저** 필요하다(이 문서는 흐름·결정 제안까지).