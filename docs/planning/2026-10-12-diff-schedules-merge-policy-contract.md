# C(diff)·D(예약 실행)·E(병합 정책) 확정 계약 — 2026-10-12

이 문서는 **계약서**다. 백엔드(14)·프론트(17) 팀은 이 문서의 규칙을 그대로 따른다. 실행 #242 이어가기(사용자가 후보 전부 구현을 지시)에서 **설계 담당(251)** 이 맡은 C·D·E 세 도메인의 **데이터 모델 · Flyway 초안 · API 계약 · 엔티티 변경점**을 못 박는다.

- 근거: `agents/CONVENTIONS.md`(이벤트 스트림 우선 갱신, record+Jakarta Validation, 도메인 우선 폴더, snake_case, 부분 유니크/부분 인덱스 주의, 변화 발행은 서비스 계층에서만) · 실측 코드(`execution/`, `task/`, `delegation/`, `event/`, `process/`, `project/`, `V1/V7/V10/V13/V14`) · `docs/planning/2026-10-08-execution-list-api-contract.md`(계약 문서 형식 선례).
- 이 문서가 **확정**이다. 다른 팀은 여기 적힌 이름·경로·번호를 바꾸지 않는다. 바꿔야 하면 이 문서를 함께 고친다.
- **다시 설계하지 않는다**: A(`GET /executions` 실행 목록)·B(요청 조회)·F(`PUT /ai-providers/{id}`·`GET /ai-providers/cli`·`/catalog`)·실행 로그 다중 스트림·채팅 첨부 복원은 이미 구현됐다. 이 문서 범위 밖이다.

## 0. 실측 사실 (설계의 제약 — 추측 아님)

- `execution` 테이블/엔티티에 **변경 파일·커밋·병합 결과가 이미 있다**(V13·V14, `execution/domain/Execution.java`): `worktree_path VARCHAR(1024)`, `worktree_branch VARCHAR(200)`, `result_commit VARCHAR(64)`, `merge_status VARCHAR(20)`(`MergeStatus`), `merge_detail TEXT`, `changed_files JSONB`(`List<ChangedFile>` = `{status, path}` 배열). **`changed_files`/`result_commit`/`merge_status` 는 트리(루트 실행)에만 채운다** — 자식은 비어 있다.
- `execution.workspace_id`(NOT NULL)가 실행 디렉터리(프로젝트 기본 워크스페이스)를 기록하고 `workspace.path`(VARCHAR(1024))가 실제 저장소 경로다(`workspace/domain/Workspace.java`). 즉 **diff 를 만들 저장소 경로는 실행에서 바로 닿는다**(워크트리가 지워졌어도 git 객체는 메인 저장소가 공유한다).
- git 은 셸을 경유하지 않고 `ProcessService.spawn(id, "git", args, cwd)` 로만 실행한다(`WorktreeService.git(...)`). `WorktreeService` 는 병합을 `Semaphore mergeLock`(공정, 1)으로 **전역 직렬화**한다 — 커밋은 각자 워크트리에서 하고 병합만 직렬화한다.
- 스케줄링은 이미 켜져 있다: `AgentDockApplication` 에 `@EnableScheduling`, 선례 `ExecutionService.expireStaleWaitingInputs()` 가 `@Scheduled(fixedDelayString="PT10M", initialDelayString="PT1M")`. **별도 스레드 풀 빈은 없다**(출력 펌프용 `StreamPumps` 캐시 풀만 있음). 그래서 "workers 풀"은 이번에 새로 정의한다(§2.4·§1.4).
- 명령 하나 = Task 하나 + 실행 트리 하나이며 진입점은 `TaskService.command(projectId, CommandRequest)` 다(`POST /projects/{id}/commands`, `ProjectCommandController`). `TaskService.command` 가 `attachmentService.attachToTask` 로 첨부를 연결하고 `delegationService.start` 로 실행을 시작한다. **예약 실행도 이 길을 그대로 재사용한다**(새 실행 로직을 발명하지 않는다).
- 전역 이벤트는 **페이로드가 아니라 알림**이다(`DataChangedEvent{type, projectId, executionId, taskId}`, `EventTypes`, 8종). 발행은 **서비스 계층에서 실제로 바뀌는 자리에만** 한다(`ExecutionService.update` 처럼). AOP 없음.
- `/settings` 라우트는 프론트에만 있고 **백엔드에 전역 설정 테이블/컨트롤러가 아직 없다**(테마는 localStorage). 그래서 E 의 `merge_policy` 는 백엔드 최초의 전역 설정 테이블이다.
- 스키마 진실은 Flyway(`apps/backend/src/main/resources/db/migration/`, 현재 **V1~V22**), JPA 는 `ddl-auto: validate`. **엔티티 필드가 컬럼과 어긋나면 기동 실패**한다. 현재 최대가 V22 이므로 이 문서는 **V23·V24·V25** 만 쓴다.
- 프로젝트 삭제는 FK 안전 순서로 지운다(`ProjectService.delete`). 새 테이블의 FK 는 `ON DELETE CASCADE`(schedule→project, schedule_run→schedule)를 써서 `delete` 코드에 분기를 더하지 않는다.
- `ExecutionService.recordTreeResult(...)` 가 병합 결과를 루트 실행에 저장하는 한 지점이다(병합 상태·커밋·사유·변경 파일). **E 의 자동 정리 훅은 여기**에 걸린다(§3.3).

---

## 1. C — 변경사항 diff (`GET /executions/{id}/diff`)

**목적**: 실행 트리의 루트가 바꾼 파일 목록(`execution.changed_files`)의 **파일별 unified diff** 를 화면(실행 트리 창의 루트 행 → 파일별 diff 창)에 보여 준다. git 은 `ProcessService` 로 실행한다(셸 미경유).

### 1.1 데이터 모델 — **변경 없음 (마이그레이션 없음)**

C 는 기존 `execution.changed_files`(JSONB)·`result_commit`·`merge_status`·`worktree_path`·`worktree_branch`(V13·V14)와 `workspace.path` 만 쓴다. **새 테이블·컬럼·인덱스가 없다.** diff 는 매 요청 git 으로 계산한다(캐시하지 않는다 — 트리 결과는 한 번 확정되면 변하지 않으므로 캐시 이득이 작고, 저장하면 정합성 유지 비용만 늘어난다).

### 1.2 엔드포인트

```
GET /executions/{id}/diff                 # 트리 루트의 변경 파일 전체
GET /executions/{id}/diff?path=<repo상대경로> # 파일 하나만
```

- 인증 없음(다른 실행 API 와 동일). `produces: application/json`.
- `id` 는 **트리의 임의 실행**(자식이어도 됨). 서버가 `rootExecutionId` 를 따라 **루트로 올라가** diff 를 만든다(`rootExecutionId == null ? id : rootExecutionId`). 응답은 루트 기준이다(트리 결과는 루트의 것).
- `path`(선택): 저장소 루트 기준 **상대 경로**. 주면 그 파일 하나만 담는다. 생략하면 `changed_files` 전체.
- **git 을 부르지 않는 경우(200, `files: []`)**: 루트의 `result_commit` 이 비었거나 `changed_files` 가 비었으면(=`NONE`, 변경 없음) git 을 실행하지 않고 빈 목록을 준다.
- 정렬: `changed_files` 에 저장된 순서(WorktreeService 가 `git diff --name-status` 순서대로 저장) 그대로. 파일 단위 정렬을 새로 하지 않는다.

### 1.3 응답 DTO (record — Jackson 순환 없음)

`com.agent.dock.execution.dto.ExecutionDiffResponse`

```java
public record ExecutionDiffResponse(
        Long executionId,        // 요청한 실행 id(그대로)
        Long rootExecutionId,    // 실제 diff 기준 루트 실행 id
        String resultCommit,     // 루트의 트리 커밋 sha(없으면 null)
        MergeStatus mergeStatus, // 루트의 병합 상태(MERGED/MANUAL/NONE, 없으면 null)
        List<FileDiff> files     // 아래. 변경 없으면 빈 목록
) {
    /** 파일 하나. git 한 파일 diff 실패는 그 파일만 diff=null + error 로 알리고 나머지를 살린다. */
    public record FileDiff(
            String status,    // changed_files 의 git 상태 코드(A/M/D/R/C…)
            String path,      // 저장소 루트 기준 상대 경로
            boolean binary,   // 바이너리 파일이면 true(diff 없음)
            boolean truncated,// MAX_DIFF_CHARS 를 넘어 잘렸으면 true
            int additions,    // diff 에서 센 '+' 줄 수(헤더 제외). binary 면 0
            int deletions,    // diff 에서 센 '-' 줄 수(헤더 제외). binary 면 0
            String diff,      // unified diff 텍스트(@@ 헌크 포함). binary/실패면 null
            String error      // 만들지 못한 사유(성공이면 null)
    ) {}
}
```

- **Jackson 순환 방지**: 엔티티를 싣지 않는다 — 원시값·문자열·enum·record 만 담는다. `MergeStatus` 는 `@Enumerated(STRING)` 이지만 DTO 에서는 이름 문자열로 직렬화된다.
- **`changed_files` 파생**: `FileDiff.status`·`path` 는 루트의 `Execution.getChangedFiles()` 에서 그대로 온다. diff 본문만 git 으로 계산해 채운다.

### 1.4 git 실행 규칙 (셸 미경유 · workers 풀 · 경로 검증)

- **cwd = 메인 저장소 경로** = 루트 실행의 `workspace.path`. 워크트리(worktree)는 메인 저장소와 git 객체를 공유하므로 **워크트리가 이미 정리됐어도 동작**한다(트리 커밋은 브랜치·병합으로 도달 가능). 워크트리 경로를 cwd 로 쓰지 않는다(지워졌을 수 있고, 존재해도 메인 저장소와 결과가 같다).
- **명령(파일별 1회)**: `git show --no-color --format= --unified=3 <resultCommit> -- <path>`. 커밋 하나(트리 결과)의 도입 변경만 나오고, 병합 후에도 커밋은 조상으로 남아 재현된다. `--format=` 로 커밋 메타 줄을 뺀다.
- **경로 검증(필수)**: `path` 는 `changed_files` 안의 값과 **정확히 일치**해야 하고(허용 목록 대조), 그 밖에는 거부한다. 추가로 `..`(상위 이동)·절대경로·선행 `-`(옵션 주입)를 막는다. 인자는 `ProcessBuilder` 로 리스트 전달(셸 미경유)이고 `--` 뒤에 둔다. 값이 목록에 없으면 **404**(§1.5).
  - 단일 조회(`?path=`)가 `changed_files` 에 없는 경로면 404. 전체 조회는 `changed_files` 만 순회하므로 이 검증을 내부적으로 항상 통과한다.
- **workers 풀 + 타임아웃**: git 호출은 **요청 스레드에서 직접 돌리지 않는다**. §2.4 에서 정의한 공용 `workers` 풀에 제출해 기다린다(각 파일·또는 전체 묶음에 **20초** 상한). 타임아웃·git 부재·비저장소·실패는 **그 파일의 `diff=null` + `error`** 로 반환하고 나머지 파일은 계속 처리한다(요청 전체를 5xx 로 만들지 않는다 — 전역 오류 링버퍼를 더럽히지 않기 위함).
- **바이너리**: 출력에 `Binary files` 또는 `GIT binary patch` 가 보이면 `binary=true`, `diff=null`, `additions=0`, `deletions=0`.
- **크기 상한**: 파일당 diff 를 `MAX_DIFF_CHARS = 200_000` 자로 자르고 `truncated=true`. `additions`/`deletions` 는 **자르기 전 전체 diff** 기준으로 센다(정확한 수치). 세는 규칙: 줄이 `+++`/`---` 로 시작하면 제외, `+` 로 시작하면 additions, `-` 로 시작하면 deletions.
- 새 서비스 `execution/service/ExecutionDiffService`(얇게). git 실행은 `WorktreeService` 와 같은 방식(`ProcessService` + `StreamPumps` 로 stdout/stderr 펌프)으로 한다 — **`WorktreeService` 의 private `git()` 를 복제하지 말고**, 공용 헬퍼(`execution/service/GitRunner`, package-private)로 승격해 `WorktreeService` 와 `ExecutionDiffService` 가 함께 쓴다(중복 제거. `WorktreeService` 의 동작·시그니처는 바꾸지 않는다).

### 1.5 오류 (400 / 404)

| 경우 | 상태 | 본문(예) |
| --- | --- | --- |
| 실행이 없음 | **404** | `Execution 123 not found` (`NotFoundException`) |
| `?path=` 가 `changed_files` 에 없음 | **404** | `File src/A.java is not part of this tree's changes` (`NotFoundException`) |
| `?path=` 가 `..`·절대경로·선행 `-` | **400** | `잘못된 파일 경로입니다` (`BadRequestException`) |
| 변경 없음(`result_commit`/`changed_files` 빔) | **200** | `{ ..., files: [] }` |
| git 이 없거나 저장소가 아니거나 파일 diff 실패 | **200** | 그 파일 `diff:null, error:"<사유>"` (요청 전체는 성공) |
| 그 외 | **200** | 위 DTO |

- `GlobalExceptionHandler` 의 기존 400/404 규칙을 그대로 쓴다(새 예외 타입 없음). 4xx 는 서버 오류 로그에 담지 않는다.

### 1.6 실시간 이벤트

**C 는 읽기 전용이라 이벤트를 발행하지 않는다.** 트리 결과(`changed_files`·`result_commit`·`merge_status`)가 바뀌면 이미 `execution.changed` 가 나가고, 화면은 그 조각만 다시 읽는다. 이미 열린 diff 창은 **다시 읽지 않는다**(확정된 트리 결과는 변하지 않는다).

### 1.7 엔티티 변경점

**기존 엔티티 변경 없음.** `Execution`·`Workspace` 를 그대로 읽기만 한다. (`ddl-auto=validate` 위험 없음.)

### 1.8 프론트 소비 (참고)

- 여는 자리: 실행 트리 창(`pages/Projects/ExecutionTree.tsx`)의 **루트 행**에서 "변경 파일" 목록 → 파일 클릭 → diff 창. 새 창을 만들지 않고 기존 `execution.module.css`·`modal`(glassStrong)·`menu` 패턴을 `composes` 로 재사용한다.
- 파일 클릭 시 `GET /executions/{rootId}/diff?path=<path>` 로 파일 하나만 읽고(전체를 한 번에 받지 않는다 — 큰 트리 보호), `status`·`additions/deletions`·`binary`·`truncated` 를 배지로 표시한다. `error` 가 있으면 그 사유를 한 줄로 보여 준다(모의 데이터로 때우지 않는다).

---

## 2. D — 반복·예약 실행 (`/schedules`)

**목적**: 프로젝트 명령을 **주기(cron)** 로 반복한다. 즉시 실행과 예약 실행 모두 **기존 `TaskService.command`(=`POST /projects/{id}/commands`)를 재사용**한다. 무거운 트리거는 **workers 풀**에서 돌려 요청 스레드·스케줄러 스레드를 막지 않는다.

### 2.1 데이터 모델 (V23 `schedule`, V24 `schedule_run`, snake_case)

**`schedule`** — 예약 정의(한 행 = 한 주기):

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGSERIAL | PK | |
| `project_id` | BIGINT | NOT NULL, FK → project(id) ON DELETE CASCADE | 소속 프로젝트 |
| `name` | VARCHAR(255) | NOT NULL | 사람이 붙인 이름 |
| `prompt` | TEXT | NOT NULL | 실행할 명령(채팅 입력과 동일) |
| `target_agent_id` | BIGINT | FK → agent(id), NULL | 대상 에이전트 |
| `group_id` | BIGINT | FK → agent_group(id), NULL | 대상 그룹(리더가 받음) |
| `attachment_ids` | JSONB | NOT NULL DEFAULT `'[]'` | 실행에 붙일 첨부 id 배열(`List<Long>`) |
| `cron_expression` | VARCHAR(120) | NOT NULL | Spring cron(6필드: `초 분 시 일 월 요일`) |
| `timezone` | VARCHAR(64) | NOT NULL DEFAULT `'Asia/Seoul'` | cron 해석 시간대(`ZoneId`) |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true | 꺼져 있으면 스케줄러가 건너뛴다 |
| `catch_up_policy` | VARCHAR(16) | NOT NULL DEFAULT `'SKIP'` | `SKIP` / `RUN_ONCE` (놓친 실행, §2.4) |
| `last_run_at` | TIMESTAMPTZ | NULL | 마지막 트리거 시각 |
| `last_task_id` | BIGINT | NULL | 마지막 트리거가 만든 Task id |
| `next_run_at` | TIMESTAMPTZ | NULL | 다음 예정 시각(스케줄러 판정 기준) |
| `created_at`/`updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
- **제약**: `CHECK (target_agent_id IS NULL OR group_id IS NULL)` — 둘 다 지정 금지(둘 다 NULL = 프로젝트 마스터가 받음, 기존 `TaskService.command` 와 같은 규칙).
- **이름은 프로젝트 안에서 유일**: `CREATE UNIQUE INDEX schedule_project_name_unique ON schedule (project_id, name)`(전체 유니크. 부분 유니크가 아니다).
- **부분 인덱스**: `CREATE INDEX idx_schedule_due ON schedule (next_run_at) WHERE enabled` — 스케줄러가 **켜진 것만** 훑도록 `WHERE enabled` 부분 인덱스를 쓴다(부분 유니크가 아니어도 부분 인덱스 문법은 같다 — `project_workspace_default_unique` 와 같은 계열 주의).
- **`cron_expression`(cron 단어 회피)**: 컬럼명은 `cron` 이 아니라 `cron_expression` 으로 둔다(도구·예약어 혼동 방지).

**`schedule_run`** — 트리거 이력(놓친/중복 실행 판정·화면 근거):

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGSERIAL | PK | |
| `schedule_id` | BIGINT | NOT NULL, FK → schedule(id) ON DELETE CASCADE | |
| `kind` | VARCHAR(16) | NOT NULL | `SCHEDULED`(스케줄러) / `MANUAL`(즉시 실행) |
| `status` | VARCHAR(16) | NOT NULL | `STARTED`(실행 시작함) / `SKIPPED`(건너뜀) / `FAILED`(시작 실패) |
| `reason` | TEXT | NULL | SKIPPED/FAILED 사유(`이전 실행이 아직 진행 중`·`놓친 실행 건너뜀` 등) |
| `task_id` | BIGINT | NULL | 만들어진 Task(있으면) |
| `root_execution_id` | BIGINT | NULL | 시작한 실행 트리 루트(있으면) |
| `scheduled_for` | TIMESTAMPTZ | NULL | 예정 시각(놓친 실행 판정) |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

- 인덱스: `CREATE INDEX idx_schedule_run_schedule ON schedule_run (schedule_id, created_at DESC)`.
- `schedule_run.status = STARTED` 는 **"실행을 시작했다"는 뜻이지 "끝났다"가 아니다**. 트리의 완료·성공/실패는 기존 `task.changed`/`execution.changed` 와 Task 상태가 담당한다(예약 도메인이 실행 상태를 이중 추적하지 않는다).

**설계 결정(명시)**:
- **놓친 실행은 쌓지 않는다**: 앱이 꺼져 있던 동안 지나간 회차를 전부 재생(RUN_ALL)하지 않는다(폭주·중복 트리 위험). 정책은 `SKIP`(건너뛰고 다음 회차로) 또는 `RUN_ONCE`(한 번만 따라잡음) 뿐이다.
- **동시 실행 중복 방지**: 같은 schedule 이 아직 트리거 중이면 두 번째는 `SKIPPED`.
- **cron 은 Spring `CronExpression`** 을 쓴다(별도 라이브러리 없음). `next_run_at` 을 DB 에 두어 껐다 켜도/재기동해도 회차가 어긋나지 않게 한다.

### 2.2 엔티티 (신규 — 마이그레이션과 정확히 일치해야 기동된다)

폴더: `schedule/`(도메인 우선 + 계층 분해) — `domain/` · `controller/` · `service/` · `repository/` · `dto/`.

- `schedule/domain/Schedule.java` (`@Table(name="schedule")`): 위 컬럼과 일치. `target_agent_id`/`group_id` 는 **관계 없이 컬럼만** 둔다(`@Column(name="target_agent_id") Long targetAgentId` — Execution 의 `task_id` 와 같은 이유: agent/group 패키지 의존을 피하고, 응답 이름은 배치 조회로 붙인다). `attachment_ids` 는 `@JdbcTypeCode(SqlTypes.JSON) @Column(name="attachment_ids", columnDefinition="jsonb") List<Long> attachmentIds`. `catch_up_policy` 는 `@Enumerated(STRING) @Column(length=16) CatchUpPolicy catchUpPolicy`.
- `schedule/domain/CatchUpPolicy.java`: `SKIP`, `RUN_ONCE`.
- `schedule/domain/ScheduleRun.java`: 위 컬럼. `kind` → `ScheduleRunKind`(`SCHEDULED`/`MANUAL`), `status` → `ScheduleRunStatus`(`STARTED`/`SKIPPED`/`FAILED`), 둘 다 `@Enumerated(STRING) @Column(length=16)`.
- `schedule/repository/ScheduleRepository.java`(`extends JpaRepository<Schedule, Long>`): `List<Schedule> findByEnabledTrueOrderByIdAsc()`, `List<Schedule> findByProjectIdOrderByIdAsc(Long projectId)`, `boolean existsByProjectIdAndName(...)` (409 판정).
- `schedule/repository/ScheduleRunRepository.java`: `List<ScheduleRun> findByScheduleIdOrderByCreatedAtDesc(Long scheduleId)`.

### 2.3 API 계약

```
GET    /schedules?projectId=     → 200 ScheduleResponse[]   (projectId 없으면 전체)
POST   /schedules                → 201 ScheduleResponse
GET    /schedules/{id}           → 200 ScheduleResponse
PUT    /schedules/{id}           → 200 ScheduleResponse
DELETE /schedules/{id}           → 204
POST   /schedules/{id}/run       → 201 {taskId, rootExecutionId}   (즉시 실행)
```

- 목록은 **봉투 없는 배열**(`/tasks`·`/executions` 와 같은 패턴), 정렬 `id asc`.
- `POST /schedules/{id}/run`: **내부적으로 `TaskService.command(projectId, CommandRequest(prompt, targetAgentId, groupId, attachmentIds))` 를 그대로 호출**한다(새 실행 로직 금지). 응답은 기존 `task/dto/CommandResponse`(=`{taskId, rootExecutionId}`) 재사용. 이때 `schedule_run(kind=MANUAL, status=STARTED)` 한 줄과 `last_run_at`/`last_task_id` 를 갱신하고 `schedule.changed` 를 발행한다. **예약 회차(`next_run_at`)는 즉시 실행으로 바뀌지 않는다**(수동 실행은 회차를 소모하지 않는다).

**요청 DTO** (`schedule/dto/ScheduleRequest`, record + Jakarta Validation):

```java
public record ScheduleRequest(
        @NotBlank String name,
        @NotBlank String prompt,
        Long targetAgentId,            // groupId 와 동시 지정 금지
        Long groupId,
        List<Long> attachmentIds,      // null 허용
        @NotBlank String cron,         // Spring cron 6필드
        String timezone,               // null 이면 서버 기본(Asia/Seoul)
        Boolean enabled,               // null 이면 기존 값 유지(생성 시 true)
        String catchUpPolicy           // null 이면 기존 값 유지(생성 시 SKIP)
) {}
```

- `PUT` 은 **전체 교체**다(부분 PATCH 아님). `enabled`/`timezone`/`catchUpPolicy` 가 null 이면 기존 값을 유지한다(프론트가 토글만 보낼 때 유용). `name`·`prompt`·`cron` 은 `@NotBlank` 라 항상 필요하다.

**응답 DTO** (`schedule/dto/ScheduleResponse`, record — 파생 이름 포함, 엔티티 미노출):

```java
public record ScheduleResponse(
        Long id, Long projectId, String name, String prompt,
        Long targetAgentId, String targetAgentName,   // 배치 조회 파생
        Long groupId, String groupName,               // 배치 조회 파생
        List<Long> attachmentIds,
        String cron, String timezone, boolean enabled, String catchUpPolicy,
        Instant lastRunAt, Long lastTaskId, Instant nextRunAt,
        Instant createdAt, Instant updatedAt
) {}
```

**교차 검증(서비스, 400)**:
1. `targetAgentId` 와 `groupId` 동시 지정 → 400 `targetAgentId 와 groupId 는 함께 쓸 수 없습니다`.
2. `targetAgentId` 가 프로젝트 소속이 아님 → 400. `groupId` 와 프로젝트 불일치 → 400.
3. `cron` 을 `CronExpression.parse` 로 검증 → 실패 시 400 `cron 표현식이 올바르지 않습니다: <cron>`.
4. `timezone` 을 `ZoneId.of` 로 검증 → 실패 시 400 `알 수 없는 시간대입니다: <tz>`.
5. 첨부 id 는 `attachmentService` 로 존재 검증(기존 `TaskService.command` 경로가 이미 검증하므로 생성 시 미리 막지 않아도 된다 — 실행 시 400/404 가 그대로 난다).

**오류 표**:

| 경우 | 상태 | 본문(예) |
| --- | --- | --- |
| 프로젝트 없음 | **404** | `Project 6 not found` |
| schedule 없음 | **404** | `Schedule 3 not found` |
| 프로젝트 안 이름 중복(생성) | **409** | `Schedule name already exists` |
| targetAgentId·groupId 동시 지정 | **400** | `targetAgentId 와 groupId 는 함께 쓸 수 없습니다` |
| 에이전트/그룹이 프로젝트 소속 아님 | **400** | `Agent 9 does not belong to project 6` |
| cron/ timezone 파싱 실패 | **400** | `cron 표현식이 올바르지 않습니다: ...` |
| 없는 schedule 실행 | **404** | `Schedule 3 not found` |

- `DELETE /schedules/{id}` 204(프론트 `request()` 가 204 처리). `schedule_run` 은 `ON DELETE CASCADE` 로 함께 지운다.

### 2.4 스케줄러 (`@Scheduled` · workers 풀 · 놓친 실행)

- **workers 풀(신규 공용 빈)**: `common/config/WorkersConfig` 에 `@Bean("workers") ThreadPoolTaskExecutor` 를 둔다 — core 2 / max 4 / queue 64 / 데몬 스레드 접두사 `agentdock-worker-`. C(diff git)·D(트리거)가 함께 쓴다. 설정: `agentdock.workers.core:2`, `agentdock.workers.max:4`, `agentdock.workers.queue:64`(코드에 박지 않는다).
- **`schedule/service/ScheduleTicker`**(`@Component`):
  - `@Scheduled(fixedDelayString = "${agentdock.schedule.tick-ms:30000}")` 한 메서드 `tick()`.
  - `tick()` 은 `scheduleRepository.findByEnabledTrueOrderByIdAsc()` 로 **켜진 것만**(부분 인덱스가 받쳐 준다) 훑는다. `next_run_at` 이 `null` 이거나 `now` 이하인 것만 대상으로 삼는다.
  - **놓친 실행 판정**: `now > next_run_at + 유예(기본 60초)` 면 "놓친 회차"로 본다.
    - `catchUpPolicy=SKIP` → 실행하지 않고 `schedule_run(kind=SCHEDULED, status=SKIPPED, reason="놓친 실행 건너뜀", scheduled_for=next_run_at)` 를 남기고 `next_run_at = cron.next(now)` 로 전진.
    - `catchUpPolicy=RUN_ONCE` → 지금 **한 번만** 실행하고 그 뒤 `next_run_at = cron.next(now)` 로 전진(밀린 회차를 반복 실행하지 않는다).
  - 놓치지 않은 정상 회차는 `trigger(scheduleId, SCHEDULED, scheduledFor=next_run_at)` 를 **workers 풀에 제출**하고 즉시 다음 회차로 넘어간다(스케줄러 스레드는 트리를 기다리지 않는다 — `TaskService.command` 는 실행을 시작하고 바로 반환한다).
  - `tick()` 자체는 `@Transactional` 을 쓰지 않는다(오래 걸리는 트리거를 감싸지 않는다 — CONVENTIONS: 느린 작업에 @Transactional 금지).
- **`schedule/service/ScheduleService`**:
  - `trigger(scheduleId, kind, scheduledFor)`: **중복 방지**로 in-memory `Set<Long> running`(ConcurrentHashMap 키셋)을 쓴다. 이미 있으면 `schedule_run(status=SKIPPED, reason="이전 실행이 아직 진행 중")` 를 남기고 끝낸다. 없으면 `running.add`, then `taskService.command(...)` → 성공 시 `schedule_run(kind, STARTED, taskId, rootExecutionId, scheduledFor)` + `last_run_at/last_task_id` 갱신, 실패(`ExecutionGuard` 409 등) 시 `schedule_run(status=FAILED, reason=예외 메시지)`. finally `running.remove`.
  - 회차 전진: 트리거 성공/건너뜀 모두 `next_run_at = CronExpression.parse(cron).next(ZonedDateTime.now(zone))` 로 갱신한다. (단, `RUN_ONCE` 로 놓친 것을 지금 실행해도 다음 회차는 `now` 기준으로 계산 — 과거 회차를 되채우지 않는다.)
  - 생성/수정/토글(on)/즉시 실행 시에도 `next_run_at` 을 같은 규칙으로 계산한다.
  - **변화 발행은 서비스 계층에서만**: CRUD·토글·트리거 이력 갱신 뒤 `changeEvents.scheduleChanged(projectId)` 한 곳에서 발행한다(§2.6).
- **설정 키**(`application.yaml` `agentdock` 아래): `schedule.tick-ms`(기본 30000), `schedule.grace-seconds`(기본 60), `schedule.enabled`(기본 true — false 면 `tick()` 이 즉시 반환, 테스트·운영 스위치).

### 2.5 오류 — §2.3 표와 동일. `POST /schedules/{id}/run` 의 실행 가드 오류(런타임 꺼짐·워크스페이스 미연결 409)는 **기존 `TaskService.command`/`ExecutionGuard` 규칙을 그대로** 낸다(새로 만들지 않는다).

### 2.6 실시간 이벤트

- 새 타입 **`schedule.changed`** 추가(`event/dto/EventTypes.SCHEDULE_CHANGED = "schedule.changed"`, `EventPublisher.scheduleChanged(Long projectId)` → `publish(SCHEDULE_CHANGED, projectId, null, null)`).
- 발행 지점(서비스 계층): `ScheduleService` 의 생성·수정·삭제·토글·`trigger`(이력/last/next 갱신 뒤). 예약 실행이 실제 트리를 시작하면 그것은 기존 `task.changed`·`execution.changed` 가 따로 발행한다.
- **프론트 소비**: `schedule.changed` 를 받으면 이벤트의 `projectId` 가 화면 필터와 맞을 때만 `GET /schedules?projectId=` 한 조각을 다시 읽는다(§1 규칙과 동일). 로그 스트림은 재구독하지 않는다.

### 2.7 검증(백엔드)

- JUnit: CRUD(201/200/204·409 이름 중복·400 교차 검증), `trigger` 성공/실패/중복 SKIP, `tick()` 이 `next_run_at` 을 지난 것만 고르고 `SKIP`/`RUN_ONCE` 를 각각 처리하는지, `schedule_run` 이력. `.\gradlew.bat test` 통과.

---

## 3. E — 병합 정책 (`GET/PUT /settings/merge-policy`)

**목적**: 트리 결과의 **자동 병합이 성공(MERGED)한 뒤의 자동 정리**를 전역 설정으로 켠다. (`WorktreeService.collect` 의 자동 병합·`ExecutionService.retryMerge` 는 그대로 두고, 성공 뒤 정리만 정책으로 자동화한다.)

### 3.1 데이터 모델 (V25 `merge_policy` — 싱글턴)

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | SMALLINT | PK, `CHECK (id = 1)` | 싱글턴(항상 1행) |
| `auto_cleanup_enabled` | BOOLEAN | NOT NULL DEFAULT false | MERGED 뒤 워크트리·브랜치 자동 정리 |
| `cleanup_delete_branch` | BOOLEAN | NOT NULL DEFAULT true | 자동 정리 시 브랜치도 삭제(`DELETE /executions/{id}/worktree?branch=true` 와 동일 의미) |
| `created_at`/`updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

- **싱글턴 방식**: 별도 키-값 테이블 대신 `id SMALLINT + CHECK (id = 1)` 로 한 행만 강제한다. 여기엔 부분 유니크 인덱스가 필요 없다(주의: 부분 유니크 인덱스는 `project_workspace` 처럼 "여러 행 중 하나"일 때 쓴다 — E 는 "딱 한 행"이라 CHECK 가 맞다).
- 시드 한 행: `INSERT INTO merge_policy (id) VALUES (1) ON CONFLICT (id) DO NOTHING;`

### 3.2 API 계약

```
GET /settings/merge-policy   → 200 MergePolicyResponse
PUT /settings/merge-policy   → 200 MergePolicyResponse
```

- 인증 없음. 프로젝트 파라미터 없음(전역).
- `PUT` 은 전체 교체. 레코드는 항상 존재한다(없으면 기본값으로 방어 생성).

**요청/응답 DTO** (`settings/dto/MergePolicyRequest`·`MergePolicyResponse`, record):

```java
public record MergePolicyRequest(
        @NotNull Boolean autoCleanupEnabled,
        @NotNull Boolean cleanupDeleteBranch
) {}

public record MergePolicyResponse(
        boolean autoCleanupEnabled,
        boolean cleanupDeleteBranch,
        Instant updatedAt
) {}
```

**오류**: 본문 누락/널 → 400(`@NotNull`, `GlobalExceptionHandler.handleValidation`). 그 외 200. 404 는 없다(싱글턴은 항상 존재).

### 3.3 서비스 통합 (자동 정리 훅)

- **`settings/domain/MergePolicy.java`**(`@Table(name="merge_policy")`, `@Id Short id`, `@GeneratedValue` 없음 — 항상 1), `settings/repository/MergePolicyRepository.java`(`findById(1)`), `settings/service/MergePolicyService.java`, `settings/controller/MergePolicyController.java`(`@RequestMapping("/settings/merge-policy")`).
- **훅 위치**: `ExecutionService.recordTreeResult(...)` 는 병합 결과를 저장하는 한 지점이다. 여기서 저장한 `mergeStatus == MERGED` 이고 정책 `autoCleanupEnabled` 면, 저장 직후 **`worktreeService.remove(worktreePath, branch, cleanupDeleteBranch)`** 를 호출하고 성공 시 실행의 `worktreePath`/`worktreeBranch` 를 비운다(기존 `removeWorktree` 와 같은 정리 후처리). 실패(정리 못함)는 **실행을 막지 않고** `merge_detail` 또는 SYSTEM 로그 한 줄로 사유를 남긴다(자동화는 부가 기능 — 실패가 결과 기록을 방해하지 않는다).
  - 정리 git 은 **workers 풀**에서 돌린다(요청 스레드·위임 스레드를 붙잡지 않는다). `MERGED` 직후 비동기 정리를 걸어도 되고, 동기로 하되 짧은 git(`worktree remove`)만 부르므로 수용 가능 — **택1은 백엔드 재량**이나, 반드시 `WorktreeService.remove` 를 재사용하고 규칙(전역 직렬화 대상 아님 — remove 는 mergeLock 밖)을 지킨다.
  - **정리해도 diff(C)는 계속 동작**한다: diff 는 `result_commit`(git 객체, 메인 저장소)으로 만들고 `worktree_path` 에 의존하지 않는다(§1.4).
- **`MergePolicyService`**: `get()`(행이 없으면 기본값으로 `save` 후 반환 — 방어), `update(request)`(저장 후 `changeEvents.settingsChanged()` 발행). `@Transactional`(짧은 DB 갱신).

### 3.4 실시간 이벤트

- 새 타입 **`settings.changed`** 추가(`EventTypes.SETTINGS_CHANGED = "settings.changed"`, `EventPublisher.settingsChanged()` → `publish(SETTINGS_CHANGED, null, null, null)` — 전역이라 식별자 없음).
- 발행 지점: `MergePolicyService.update`. **G(비용·예산)의 전역 설정도 이 타입을 재사용**할 수 있다(설계 252 와 맞춘다 — G 가 별도 타입을 원하면 이 문서를 고친다).
- **프론트 소비**: `settings.changed` 를 받으면 설정 화면의 **병합 정책 절만** `GET /settings/merge-policy` 로 다시 읽는다.

### 3.5 엔티티 변경점

**기존 엔티티 변경 없음.** `Execution`·`WorktreeService` 시그니처를 바꾸지 않는다(자동 정리는 기존 public 메서드 `WorktreeService.remove`·`ExecutionService.update` 재사용).

---

## 4. Flyway 초안 (추가 전용 · 실제 .sql 파일은 만들지 않는다 — 이 블록이 계약)

> 규칙: `V<N>__<설명>.sql` 새 버전으로만 추가(기존 파일 수정 금지). 멱등(`IF NOT EXISTS`/`ON CONFLICT`)을 지킨다. 현재 최대 V22 → **V23·V24·V25**.
> **번호 예약(중요, §7)**: 이 문서가 **V23·V24·V25 를 쓴다.** 다른 팀(백엔드 247·설계 252 등)은 **V26 부터** 쓴다.

### V23__schedule.sql

```sql
-- 예약(반복) 실행 정의. 프로젝트 명령을 cron 주기로 반복한다.
-- 즉시 실행·예약 실행 모두 기존 TaskService.command(=POST /projects/{id}/commands)를 재사용한다.
CREATE TABLE IF NOT EXISTS schedule (
    id               BIGSERIAL PRIMARY KEY,
    project_id       BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    prompt           TEXT NOT NULL,
    target_agent_id  BIGINT REFERENCES agent(id),
    group_id         BIGINT REFERENCES agent_group(id),
    attachment_ids   JSONB NOT NULL DEFAULT '[]',
    cron_expression  VARCHAR(120) NOT NULL,
    timezone         VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    enabled          BOOLEAN NOT NULL DEFAULT true,
    catch_up_policy  VARCHAR(16) NOT NULL DEFAULT 'SKIP',
    last_run_at      TIMESTAMPTZ,
    last_task_id     BIGINT,
    next_run_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT schedule_target_exclusive CHECK (target_agent_id IS NULL OR group_id IS NULL)
);

-- 프로젝트 안에서 이름은 유일(전체 유니크).
CREATE UNIQUE INDEX IF NOT EXISTS schedule_project_name_unique ON schedule (project_id, name);

-- 스케줄러가 켜진 것만 훑도록 부분 인덱스(WHERE enabled).
CREATE INDEX IF NOT EXISTS idx_schedule_due ON schedule (next_run_at) WHERE enabled;
```

### V24__schedule_run.sql

```sql
-- 예약 트리거 이력(놓친/중복 실행 판정·화면 근거). status=STARTED 는 "실행을 시작했다"일 뿐,
-- 트리의 완료·성공/실패는 기존 task/execution 이 담당한다(예약 도메인이 이중 추적하지 않는다).
CREATE TABLE IF NOT EXISTS schedule_run (
    id                BIGSERIAL PRIMARY KEY,
    schedule_id       BIGINT NOT NULL REFERENCES schedule(id) ON DELETE CASCADE,
    kind              VARCHAR(16) NOT NULL,   -- SCHEDULED | MANUAL
    status            VARCHAR(16) NOT NULL,   -- STARTED | SKIPPED | FAILED
    reason            TEXT,
    task_id           BIGINT,
    root_execution_id BIGINT,
    scheduled_for     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_schedule_run_schedule ON schedule_run (schedule_id, created_at DESC);
```

### V25__merge_policy.sql

```sql
-- 병합 정책(전역 싱글턴). 트리 결과가 MERGED 로 끝나면 워크트리·브랜치를 자동 정리할지 정한다.
CREATE TABLE IF NOT EXISTS merge_policy (
    id                    SMALLINT PRIMARY KEY,
    auto_cleanup_enabled  BOOLEAN NOT NULL DEFAULT false,
    cleanup_delete_branch BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT merge_policy_singleton CHECK (id = 1)
);

INSERT INTO merge_policy (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
```

---

## 5. 엔티티 변경점 총괄 (`ddl-auto=validate` — 어긋나면 기동 실패)

| 대상 | 변경 | 비고 |
| --- | --- | --- |
| **기존 엔티티** | **변경 없음** | `Execution`·`Workspace`·`Task`·`Project` 시그니처·컬럼 그대로. C/D/E 모두 새 테이블과 기존 컬럼만 쓴다 → **validate 위험 없음**. |
| `schedule/domain/Schedule.java` | **신규** | V23 `schedule` 과 컬럼 1:1. `target_agent_id`/`group_id` 는 **관계 없이 컬럼만**(`Long`), `attachment_ids` 는 `@JdbcTypeCode(SqlTypes.JSON) List<Long>`, `catch_up_policy` 는 `@Enumerated(STRING) CatchUpPolicy`. |
| `schedule/domain/ScheduleRun.java` | **신규** | V24 `schedule_run` 과 1:1. `kind`/`status` 는 `@Enumerated(STRING) @Column(length=16)`. |
| `settings/domain/MergePolicy.java` | **신규** | V25 `merge_policy` 와 1:1. `@Id Short id`(자동생성 없음, 항상 1). `SMALLINT ↔ Short` 매핑 유지. |

> 주의: `enabled`·`catch_up_policy`·`kind`·`status` 는 `@Enumerated(STRING)`(ORDINAL 금지). JSONB 컬럼은 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition="jsonb"`(기존 `changed_files`·`question_options`·`handoff` 와 동일 패턴).

---

## 6. 실시간 이벤트 타입 추가 (`event/dto/EventTypes` + `EventPublisher`)

| 타입 | 추가 | 발행 지점(서비스 계층에서만) | 식별자 |
| --- | --- | --- | --- |
| `schedule.changed` | `SCHEDULE_CHANGED` | `ScheduleService` 생성·수정·삭제·토글·trigger(이력/last/next 갱신) | `projectId` |
| `settings.changed` | `SETTINGS_CHANGED` | `MergePolicyService.update`(G 의 전역 설정도 재사용 가능) | 없음(null) |

- `EventPublisher` 에 `scheduleChanged(Long projectId)`·`settingsChanged()` 를 추가한다(기존 메서드 형태 그대로).
- **C(diff)는 이벤트를 발행하지 않는다**(읽기 전용).
- 프론트 규칙(불변): 이벤트는 알림일 뿐 — 받으면 **바뀐 조각만 기존 REST 로 재조회**한다. 주기적 전체 폴링 금지.

---

## 7. 마이그레이션 번호 · 다른 팀과 맞출 것 (충돌 방지)

- **이 문서(설계 251, C/D/E)가 예약하는 번호: `V23`(schedule)·`V24`(schedule_run)·`V25`(merge_policy).** 프롬프트가 준 범위(V23~V25)를 전부 쓴다.
- **다른 도메인은 `V26` 부터**: G/H(설계 252 는 V26~V28, 백엔드 247 은 V26+)는 V26+ 를 쓴다. **V23~V25 는 이 문서 몫**이다.
- 백엔드 도메인 담당(248, D 예약)의 "V23 하나만" 규칙과 **V23=schedule 은 일치**한다. schedule_run 은 **V24** 로 분리한다(248 은 V23 에 schedule 만, V24 는 이 문서가 예약).
- 이벤트 이름은 §6 의 `schedule.changed`·`settings.changed` 로 고정한다(프론트 249·250 과 맞춘다).
- 프론트 화면(249)·데이터(250)가 필요한 스토어 필드/미지원 API 는 각자의 공유 노트(`프론트화면18.md`·`프론트데이터19.md`)와 이 문서의 DTO 필드를 대조해 맞춘다.

---

## 8. 한 커밋/검증 범위 (참고)

- **C**: `ExecutionDiffService`·`GitRunner`(승격)·`ExecutionDiffResponse`·`ExecutionController`(+`@GetMapping("/{id}/diff")`)·`WorktreeService`(git 헬퍼 공용화, 동작 불변)·테스트(루트 해석·path 검증·빈 변경·binary/truncated·git 실패 시 per-file error).
- **D**: `V23`·`V24`·`schedule/` 도메인 전체·`ScheduleTicker`·`WorkersConfig`·`application.yaml` 키·테스트(CRUD·trigger·tick·정책).
- **E**: `V25`·`settings/` 도메인·`ExecutionService.recordTreeResult` 훅·테스트(MERGED 시 자동 정리, 정책 off 시 미정리, diff 가 정리 후에도 동작).
- 검증: `.\gradlew.bat test` · (프론트 소비는 17/18/19 담당) `npm run build --workspace=apps/frontend` · 8081/3031 실측(diff 창·예약 즉시 실행·정책 저장 후 MERGED 자동 정리).
- **하지 않음**: A/B/F·로그 스트림·첨부 복원 재설계, 기존 엔티티/컬럼 변경, V22 이하 파일 수정, RUN_ALL(밀린 회차 전부 실행), diff 결과 DB 캐시.
