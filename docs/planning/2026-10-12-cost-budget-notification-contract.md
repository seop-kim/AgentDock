# G(비용·예산)·H(알림 채널) 확정 계약 (2026-10-12)

이 문서는 **계약서**다. 실행 #242 이어가기에서 아직 없는 두 도메인 — **G 비용·예산 관리**, **H 알림 채널 확장(웹훅/Slack/메일)** — 의 데이터 모델·Flyway 초안·API 계약·엔티티 변경점을 못 박는다. 백엔드(도메인 14 · API 25)·프론트(데이터 19 · 화면 18) 팀은 이 문서의 규칙을 그대로 따른다.

근거: `agents/CONVENTIONS.md`(실시간 갱신 이벤트 스트림 우선, record+Validation DTO, 도메인 우선 폴더, snake_case, 부분 유니크 인덱스 주의, 변화는 서비스 계층에서만 발행, ddl-auto=validate) · `docs/planning/2026-10-08-execution-list-api-contract.md`(목록/집계 계약) · `docs/planning/2026-10-08-frontend-data-wiring.md`(없는 API 3 — 프로젝트/전역 집계) · 실측 코드(`execution/domain/Execution.java`, `execution/dto/ExecutionStatsResponse.java`, `event/`, `common/exception/`, `V1·V7·V10`).

**범위 밖(다시 설계하지 않는다)**: 이미 구현된 A 실행 목록(`GET /executions`·`ExecutionSummaryResponse`) · B 요청 조회(projectId+status) · F 런타임 설정(`PUT /ai-providers/{id}`·`GET /ai-providers/cli`·`/catalog`) · 첨부 복원 · 실행 로그 다중 스트림. 이들은 **건드리지 않는다**. C/D/E(diff·예약·병합 정책)는 별도 계약(`2026-10-12-diff-schedules-merge-policy-contract.md`) 소관이다.
## 0. 실측 사실 (설계의 제약 — 추측 아님)

- `execution` 은 계측값을 **이미 컬럼으로 갖는다**(V10) — `input_tokens`·`output_tokens`·`cache_read_tokens`·`cache_creation_tokens`(INT), `cost_usd`(NUMERIC(12,6)), `duration_ms`(BIGINT), `num_turns`(INT), `session_id`(VARCHAR(64)). 엔티티 `Execution` 이 그대로 매핑한다. **비용·토큰은 CLI 가 준 값만 저장한다(추정 금지)** — Command Code 처럼 비용 필드가 없으면 `null` 이다(`cost_usd` null).
- `execution` 에는 **`project_id` 컬럼이 없다.** 프로젝트는 오직 `execution.task_id → task.project_id` 로 닿는다(목록 계약 §0). 집계도 이 경로를 쓴다 — 기존 `ExecutionService.stats` 가 `taskRepository.findIdsByProjectId(projectId)` → `executionRepository.findUsageByTaskIds(ids)` 로 하는 것과 같은 방식.
- **집계 엔드포인트가 이미 있다**: `GET /executions/summary` → `ExecutionStatsResponse`(실행 수·진행 수·총비용·입출력 토큰 + byProject/byGroup). 단 **시간 창(기간)이 없다**(전 기간 합계). G 의 "기간별"은 이 엔드포인트를 **고치지 않고**(A 계약 유지) 새 엔드포인트로 추가한다.
- `event/dto/EventTypes` 는 문자열 상수 모음이고 `DataChangedEvent(type, projectId, executionId, taskId)` 는 **페이로드가 아니라 알림**이다. 발행은 `event/service/EventPublisher` 의 얇은 메서드로만 하고 **서비스 계층의 "데이터가 실제로 바뀌는 자리"에서만** 부른다(AOP 없음).
- 오류는 `common/exception` 의 `BadRequestException`(400)·`NotFoundException`(404)·`ForbiddenException`(403)·`ConflictException`(409)를 던지면 `GlobalExceptionHandler` 가 `{statusCode, error, message}` 로 내려 준다. 새 오류 형식을 만들지 않는다.
- **자격증명 원칙(CONVENTIONS §런타임 연결과 자격증명)**: "자격증명은 CLI 의 기존 로그인 세션을 그대로 사용한다. 앱은 토큰/키를 저장하지 않는다." 이 원칙은 알림 채널에도 적용한다 — **비밀값은 DB 에 저장하지 않고 환경변수 이름(secret_ref)만 저장**한다(기존 `DATABASE_PASSWORD`·`CLAUDE_CODE_BIN` override 와 같은 결).
- 부분 유니크 인덱스 선례: `project_workspace_default_unique ON project_workspace (project_id) WHERE is_default`(V7). **PostgreSQL 은 유니크 인덱스에서 NULL 을 서로 다른 값으로 본다** — nullable 범위 열(`notification_rule.project_id`)에 유니크를 걸면 전역 규칙이 중복 생성된다. 그래서 범위별 **부분 유니크 인덱스 2개**로 나눈다(§1 H-2).
- 마이그레이션은 **추가 전용**, 현재 최대 `V22`. G/H 는 **V26~V28** 을 쓴다(C/D/E 는 V23~V25 — §5 경고 참고).
## 1. 데이터 모델 (snake_case · 테이블·컬럼·제약·인덱스)

도메인 우선 폴더(`agents/CONVENTIONS.md`): G 는 `budget/` 도메인(예산)으로 두고, **기간별 사용량 집계는 실행 계측을 읽으므로 `execution/` 안에** 둔다(기존 `stats` 와 같은 자리). H 는 `notification/` 도메인 하나로 모은다.

### G-1. 테이블 `project_budget` (프로젝트 예산 — 월 단위)

프로젝트당 **활성 예산 1개**다(이력은 남기지 않는다 — 필요해지면 추가 전용으로 확장).

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGSERIAL | PK | |
| `project_id` | BIGINT | NOT NULL, FK `project(id)` ON DELETE CASCADE | 프로젝트당 1개(유니크) |
| `monthly_amount_usd` | NUMERIC(12,2) | NOT NULL, CHECK > 0 | 월 예산(달러) |
| `warn_threshold_percent` | INT | NOT NULL DEFAULT 80, CHECK 1..100 | 경고 임계(%) — 기본 80 |
| `notified_month` | VARCHAR(7) | NULL | **이번 달 경고를 이미 발행한 달 키** `"2026-10"`. 같은 달에 반복 경고하지 않는다 |
| `notified_at` | TIMESTAMPTZ | NULL | 그 경고를 발행한 시각 |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

인덱스/제약:
- `CONSTRAINT project_budget_project_unique UNIQUE (project_id)` — 프로젝트당 1개.
- `CHECK (monthly_amount_usd > 0)`, `CHECK (warn_threshold_percent BETWEEN 1 AND 100)`.
- (부분 유니크 주의는 H 의 `notification_rule` 에 있다 — 여기서는 단순 유니크로 충분하다.)

**월 경계**: 회계 기준은 **UTC** 다. "이번 달"은 `date_trunc('month', now() AT TIME ZONE 'UTC')`.
### H-1. 테이블 `notification_channel` (채널)

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGSERIAL | PK | |
| `kind` | VARCHAR(16) | NOT NULL, CHECK IN ('WEBHOOK','SLACK','EMAIL') | 채널 종류 |
| `name` | VARCHAR(255) | NOT NULL | 표시 이름 |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true | 끄면 어떤 규칙도 이 채널로 보내지 않는다 |
| `config` | JSONB | NOT NULL DEFAULT '{}' | **비밀이 아닌** 설정만. EMAIL: `{host,port,from,username?,tls?}`, WEBHOOK/SLACK: `{}` |
| `secret_ref` | VARCHAR(128) | NULL | **비밀값을 담은 환경변수 이름**(값 아님). WEBHOOK=URL, SLACK=incoming webhook URL, EMAIL=SMTP 비밀번호 |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

- **비밀값은 어디에도 저장하지 않는다.** 발송 시점에 `System.getenv(secretRef)` 로 읽는다(없으면 409 — §3.3). 응답은 값을 내려 주지 않고 `secretResolved`(환경변수 존재 여부)만 파생해 준다.
- `config` 에 `url`·`password`·`token` 류 키가 오면 **400** 으로 막는다(비밀값을 config 로 흘리는 실수 방지).
- CHECK: `kind IN ('WEBHOOK','SLACK','EMAIL')`. 인덱스: 규칙이 `channel_id` 로 조회하므로 별도 인덱스 없음.

### H-2. 테이블 `notification_rule` (규칙 — 어떤 이벤트를 어느 채널로)

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGSERIAL | PK | |
| `channel_id` | BIGINT | NOT NULL, FK `notification_channel(id)` ON DELETE CASCADE | 삭제하면 규칙도 함께 사라진다 |
| `event_type` | VARCHAR(48) | NOT NULL | 알림 이벤트(§3.2 enum `NotificationEventType`) |
| `project_id` | BIGINT | NULL, FK `project(id)` ON DELETE CASCADE | **NULL = 전 프로젝트(전역)**. 있으면 그 프로젝트 이벤트만 |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

**부분 유니크 인덱스 주의(핵심)**: 같은 (채널·이벤트·범위) 규칙은 하나만 존재해야 한다. 그런데 `project_id` 가 nullable 이라 평범한 `UNIQUE (channel_id, event_type, project_id)` 로는 **전역 규칙(NULL) 이 여러 개 생긴다**(NULL 은 서로 다르다고 취급). 그래서 **부분 유니크 인덱스 두 개**로 나눈다:

```sql
-- 프로젝트 범위 규칙: (채널, 이벤트, 프로젝트) 하나만
CREATE UNIQUE INDEX notification_rule_scoped_unique
    ON notification_rule (channel_id, event_type, project_id) WHERE project_id IS NOT NULL;
-- 전역 규칙: (채널, 이벤트) 하나만 (NULL 중복 금지)
CREATE UNIQUE INDEX notification_rule_global_unique
    ON notification_rule (channel_id, event_type) WHERE project_id IS NULL;
```

- 위반은 서비스가 잡아 **409**("이미 있습니다"). DB 는 최후의 방어선으로 둔다.
- 추가 인덱스: `CREATE INDEX idx_notification_rule_event ON notification_rule (event_type);`(이벤트 기준 조회).
### H-3. 테이블 `notification_delivery` (발송 기록 — 감사·테스트 결과)

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | BIGSERIAL | PK | |
| `channel_id` | BIGINT | NOT NULL, FK `notification_channel(id)` ON DELETE CASCADE | |
| `rule_id` | BIGINT | NULL, FK `notification_rule(id)` ON DELETE SET NULL | 테스트 발송이면 NULL |
| `event_type` | VARCHAR(48) | NULL | 테스트면 `TEST` |
| `project_id` | BIGINT | NULL | 범위(전역이면 NULL) |
| `status` | VARCHAR(16) | NOT NULL, CHECK IN ('SENT','FAILED') | |
| `http_status` | INT | NULL | HTTP 응답 코드(WEBHOOK/SLACK). 없으면 NULL |
| `detail` | TEXT | NULL | 실패 사유·응답 일부 |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

- 인덱스: `CREATE INDEX idx_notification_delivery_channel ON notification_delivery (channel_id, id DESC);`(채널별 최근 기록).
- 기록은 **발송 시도마다** 남긴다. 값싼 insert 이고, 실패 원인을 화면에서 보게 한다.

## 2. Flyway 마이그레이션 SQL 초안 (V26~V28 · 추가 전용 · 실제 .sql 파일은 만들지 않음)

> 번호 규칙: C/D/E=V23~V25, G/H=**V26~V28**. 기존 파일은 **수정하지 않는다**. 멱등 스타일(`IF NOT EXISTS`)은 기존 마이그레이션 관례를 따른다.

### V26 — G: 프로젝트 예산

```sql
-- G: 프로젝트 예산(월 단위). 프로젝트당 1개. 값은 사람이 넣고, 사용량은 execution 계측값만 더한다(추정 금지).
CREATE TABLE IF NOT EXISTS project_budget (
    id                     BIGSERIAL PRIMARY KEY,
    project_id             BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    monthly_amount_usd     NUMERIC(12,2) NOT NULL,
    warn_threshold_percent INT NOT NULL DEFAULT 80,
    notified_month         VARCHAR(7),
    notified_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT project_budget_project_unique UNIQUE (project_id),
    CONSTRAINT project_budget_amount_positive CHECK (monthly_amount_usd > 0),
    CONSTRAINT project_budget_threshold_range CHECK (warn_threshold_percent BETWEEN 1 AND 100)
);
```
### V27 — H: 알림 채널

```sql
-- H: 알림 채널(웹훅/Slack/메일). 비밀값은 저장하지 않는다 — secret_ref 는 **환경변수 이름**이다.
CREATE TABLE IF NOT EXISTS notification_channel (
    id          BIGSERIAL PRIMARY KEY,
    kind        VARCHAR(16) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT true,
    config      JSONB NOT NULL DEFAULT '{}',
    secret_ref  VARCHAR(128),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notification_channel_kind CHECK (kind IN ('WEBHOOK','SLACK','EMAIL'))
);
```

### V28 — H: 알림 규칙 + 발송 기록

```sql
-- H: 규칙(= 어떤 이벤트를 어느 채널로)과 발송 기록.
CREATE TABLE IF NOT EXISTS notification_rule (
    id          BIGSERIAL PRIMARY KEY,
    channel_id  BIGINT NOT NULL REFERENCES notification_channel(id) ON DELETE CASCADE,
    event_type  VARCHAR(48) NOT NULL,
    project_id  BIGINT REFERENCES project(id) ON DELETE CASCADE,
    enabled     BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- NULL 은 서로 다른 값으로 취급되므로 전역 규칙과 프로젝트 규칙을 각각 부분 유니크 인덱스로 막는다.
CREATE UNIQUE INDEX IF NOT EXISTS notification_rule_scoped_unique
    ON notification_rule (channel_id, event_type, project_id) WHERE project_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS notification_rule_global_unique
    ON notification_rule (channel_id, event_type) WHERE project_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_notification_rule_event ON notification_rule (event_type);

CREATE TABLE IF NOT EXISTS notification_delivery (
    id          BIGSERIAL PRIMARY KEY,
    channel_id  BIGINT NOT NULL REFERENCES notification_channel(id) ON DELETE CASCADE,
    rule_id     BIGINT REFERENCES notification_rule(id) ON DELETE SET NULL,
    event_type  VARCHAR(48),
    project_id  BIGINT,
    status      VARCHAR(16) NOT NULL,
    http_status INT,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notification_delivery_status CHECK (status IN ('SENT','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_channel ON notification_delivery (channel_id, id DESC);
```
## 3. API 계약 (record + Jakarta Validation · 오류 400/403/404/409 · 실시간 이벤트)

공통 규칙(CONVENTIONS §코딩 컨벤션): 요청 `record` + `@Valid`, 응답은 엔티티가 아닌 `record`(순환 방지 — **관계 대신 id·평평한 필드만**), POST **201**, DELETE **204**, PUT 은 **200**(멱등 upsert 포함). Controller 는 얇게, 로직은 Service. 모든 오류는 `common/exception` 예외로 던진다.

### 3.1 G — 기간별 사용량 집계 · 예산

도메인 배치: 집계 응답/서비스는 `execution/`(기존 `stats` 옆), 예산은 `budget/`.

#### `GET /executions/usage?projectId=&from=&to=&bucket=`

- 기간별 사용량을 **버킷(일/주/월)** 으로 나눠 준다. `projectId` 없으면 전역. 기존 `/executions/summary` 는 **그대로 둔다**(시간 창 없는 전 기간 합계 — A 계약 보존). 리터럴 경로 `usage` 는 `/{id}` 보다 먼저 매칭된다(`/summary`·`/stream` 선례와 같다).
- 파라미터

| 이름 | 타입 | 기본 | 설명 |
| --- | --- | --- | --- |
| `projectId` | Long | 없음(전역) | 없으면 전 프로젝트 |
| `from` | ISO-8601 Instant | 이번 달 1일 00:00 UTC | 구간 시작(포함) |
| `to` | ISO-8601 Instant | now | 구간 끝(배타) |
| `bucket` | `DAY`\|`WEEK`\|`MONTH` | `DAY` | 버킷 크기 |

- **회계 시각(accounting timestamp)** = `COALESCE(finished_at, created_at)` UTC. 도는 실행은 아직 `finished_at` 이 없지만, 계측값도 아직 없다(CLI 가 끝나야 채워진다) — 그래서 합계에는 사실상 끝난 실행만 들어간다.
- **추정 금지**: 값은 `execution` 의 `cost_usd`·`input_tokens`·`output_tokens`·`cache_read_tokens`·`cache_creation_tokens` 를 그대로 더한다. `null` 은 합계에서 0 으로 더하고(값이 없다는 사실을 왜곡하지 않는다) 실행 수에는 포함한다.
응답 `record UsageResponse` (`execution/dto/UsageResponse.java`):

```java
public record UsageResponse(
        Long projectId,
        Instant from,
        Instant to,
        String bucket,                 // DAY | WEEK | MONTH
        BigDecimal totalCostUsd,       // null 없이 ZERO 로 정규화
        long totalInputTokens,
        long totalOutputTokens,
        long totalCacheReadTokens,
        long totalCacheCreationTokens,
        long executionCount,
        List<UsageBucket> buckets,
        BudgetStatus budget            // 예산이 없으면 null
) {
    public record UsageBucket(
            String key,                // "2026-10-12" / "2026-W41" / "2026-10"
            Instant start, Instant end,
            BigDecimal costUsd,
            long inputTokens, long outputTokens,
            long executionCount) {
    }

    public record BudgetStatus(
            BigDecimal amountUsd,
            BigDecimal spentUsd,       // 이번 달(현재 기간) 합계
            int percent,               // floor(spent / amount * 100)
            int warnThresholdPercent,
            String level) {            // OK | WARN | EXCEEDED
    }
}
```

- `level`: `spent >= amount` → `EXCEEDED`, `percent >= warnThresholdPercent` → `WARN`, 그 외 `OK`.
- 구현: `taskRepository.findIdsByProjectId`(프로젝트일 때) → `execution` 에서 기간 내 최소 필드만 **JPQL 프로젝션**으로 읽어 **Java 에서 버킷팅**한다(PostgreSQL `date_trunc` 는 DB 종속 — Java 버킷팅이 이식성·테스트에 낫다). `task_id`·`created_at` 로 좁힌다.
#### 예산 CRUD — `budget/` 도메인

- `GET /projects/{id}/budget` → `BudgetResponse`. **없으면 404 `Budget for project <id> not found`** — 화면은 404 를 "예산 미설정"으로 처리한다(다른 `GET /projects/{id}/workspaces/{ws}` 도 없는 할당에 404 를 쓴다).
- `PUT /projects/{id}/budget` → `BudgetResponse`(200). **upsert**(없으면 만들고 있으면 수정). 프로젝트가 없으면 404.
- `DELETE /projects/{id}/budget` → 204(없어도 204 — 멱등). 프로젝트가 없으면 404.

```java
public record BudgetRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amountUsd,
        @Min(1) @Max(100) Integer warnThresholdPercent,   // null 이면 80
        Boolean resetNotified                              // true 면 notified_month 초기화(선택)
) {}

public record BudgetResponse(
        Long projectId,
        BigDecimal amountUsd,
        int warnThresholdPercent,
        BigDecimal spentUsd,          // 이번 달 합계
        int percent,
        String level,                 // OK | WARN | EXCEEDED
        String notifiedMonth,         // "2026-10" 또는 null
        Instant createdAt, Instant updatedAt
) {}
```

- `resetNotified=true`(선택)는 `notified_month` 를 비워 다음 계산에서 다시 경고할 수 있게 한다(기본 false).
**80% 경고 발행**: `BudgetService` 가 **주기 검사**(`@Scheduled`)로 프로젝트별 이번 달 지출을 계산해 `percent >= warnThresholdPercent` 이고 `notified_month != 이번달` 이면 `notified_month`/`notified_at` 를 저장하고 **`budget.changed` 를 발행**한다(+ H 규칙이 있으면 `BUDGET_WARNING` 채널 발송). 같은 달에 두 번 뜨지 않는다.
- 주기는 설정값(`agentdock.budget.check-interval-ms`, 기본 300000)으로 두고 코드에 박지 않는다. 검사 자체는 프로젝트당 집계 1회로 **싸지만**, 스케줄러 스레드를 막지 않도록 **workers 풀**에서 돈다(무거운 git·스케줄러와 같은 원칙).
- **예산 초과 시 동작은 경고만(확정 기본)**. 실행 차단·승인 흐름은 이 계약에 넣지 않는다 — 253(화면 흐름)의 결정 뒤 추가 전용으로 다룬다. 그래서 "초과에 대한 409" 는 이 계약에 없다.

`application.yaml` 추가(값은 데이터, 코드에 박지 않음):

```yaml
agentdock:
  budget:
    warn-default-percent: 80
    check-interval-ms: 300000
  notifications:
    egress-enabled: true        # false 면 모든 외부 발송/테스트를 403 으로 거부
    timeout-ms: 5000            # 채널별 발송 제한(초과 시 FAILED)
    max-deliveries-per-event: 20
```
### 3.2 H — 알림 채널 · 규칙 · 테스트 발송

`notification/` 도메인. `NotificationEventType`(서버 enum, `notification/domain/`):

```
EXECUTION_SUCCEEDED, EXECUTION_FAILED, EXECUTION_CANCELLED,
WAITING_INPUT, MERGE_MANUAL, BUDGET_WARNING
```

(전역 스트림 `EventTypes` 의 `execution.changed` 같은 **변경 알림과 다른 축**이다 — 이건 "사람에게 알릴 사건" 목록이다.)

#### 채널 CRUD

- `GET /notifications/channels` → `List<NotificationChannelResponse>`
- `POST /notifications/channels` → 201 `NotificationChannelResponse`
- `PUT /notifications/channels/{id}` → 200 `NotificationChannelResponse`(없으면 404)
- `DELETE /notifications/channels/{id}` → 204(없으면 404; 규칙·기록은 CASCADE)

```java
public record NotificationChannelRequest(
        @NotNull NotificationChannelKind kind,
        @NotBlank @Size(max = 255) String name,
        Boolean enabled,                  // null 이면 true
        Map<String, Object> config,       // 비밀 아님. null 이면 {}
        @Size(max = 128) String secretRef // 비밀값을 담은 **환경변수 이름**
) {}

public record NotificationChannelResponse(
        Long id,
        NotificationChannelKind kind,
        String name,
        boolean enabled,
        Map<String, Object> config,
        String secretRef,
        boolean secretResolved,           // 환경변수가 존재하는지(값은 내려 주지 않는다)
        Instant createdAt, Instant updatedAt
) {}
```

- **검증 규칙(서비스)**:
  - `WEBHOOK`/`SLACK`: `secretRef` 필수(비어 있으면 **400** "웹훅 URL 을 담은 환경변수 이름이 필요합니다").
  - `EMAIL`: `config.host`·`config.port`·`config.from` 필수(없으면 **400**). `secretRef` 는 SMTP 비밀번호 환경변수(없으면 발송 때 409).
  - `config` 에 `url`·`password`·`token` 키가 있으면 **400**(비밀값을 config 로 넣지 않게).
- `secretResolved` 는 저장하지 않는 **파생값**이다(`System.getenv(secretRef) != null && !blank`).
#### 규칙 CRUD

- `GET /notifications/rules?channelId=&projectId=` → `List<NotificationRuleResponse>`
- `POST /notifications/rules` → 201 / `PUT /notifications/rules/{id}` → 200(없으면 404) / `DELETE /notifications/rules/{id}` → 204(없으면 404)

```java
public record NotificationRuleRequest(
        @NotNull Long channelId,
        @NotNull NotificationEventType eventType,
        Long projectId,                  // null = 전역
        Boolean enabled                  // null 이면 true
) {}

public record NotificationRuleResponse(
        Long id, Long channelId, NotificationEventType eventType,
        Long projectId, boolean enabled, Instant createdAt, Instant updatedAt
) {}
```

- 없는 `channelId`/`projectId` → **404**. 중복 (채널·이벤트·범위) → **409** "같은 채널·이벤트·범위 규칙이 이미 있습니다"(부분 유니크 인덱스 위반을 서비스가 먼저 잡는다).

#### 테스트 발송

- `POST /notifications/channels/{id}/test` → 201 `NotificationDeliveryResponse`
  - 채널이 없으면 **404**, `secretRef` 환경변수를 못 찾으면 **409** "자격증명(환경변수 <ref>)이 설정되지 않았습니다", 서버 egress 가 꺼져 있으면 **403**.
  - `event_type='TEST'`, `rule_id=null` 로 기록을 남기고 결과(성공/실패·HTTP 코드·사유)를 그대로 돌려준다. 타임아웃은 `agentdock.notifications.timeout-ms`(기본 5초) — 초과 시 `FAILED`.

#### 발송 기록(감사)

- `GET /notifications/deliveries?channelId=&limit=&beforeId=` → `List<NotificationDeliveryResponse>` — `/tasks`·`/executions` 와 같은 **봉투 없는 배열, 최신순(id desc), beforeId 커서**(목록 계약 패턴 재사용).

```java
public record NotificationDeliveryResponse(
        Long id, Long channelId, Long ruleId, String eventType, Long projectId,
        String status, Integer httpStatus, String detail, Instant createdAt) {}
```
#### 발송 흐름(서비스 계층)

- **발송 트리거는 변화가 실제로 일어나는 서비스 계층 한 곳**에서 한다(전역 스트림 발행과 같은 자리):
  - `ExecutionService` — 실행이 끝 상태(`SUCCEEDED`/`FAILED`/`CANCELLED`)로 전이할 때, `WAITING_INPUT` 으로 갈 때(`DelegationService`), `MANUAL` 병합일 때. 여기서 `NotificationDispatchService.dispatch(eventType, projectId, executionId, taskId)` 를 부른다.
  - `BudgetService` — 월 임계 도달 시 `BUDGET_WARNING`.
- `dispatch` 는 규칙(`event_type` 일치 AND (`project_id IS NULL` OR `project_id = 이벤트 project`) AND `enabled`)과 채널(`enabled`)을 골라 **workers 풀에서 비동기**로 보낸다(요청·실행 스레드를 막지 않는다). 결과마다 `notification_delivery` 한 줄.
- 채널 발송:
  - `WEBHOOK`/`SLACK`: `secret_ref` 환경변수의 URL 로 JSON POST. Slack 은 `{"text": ...}` 형태.
  - `EMAIL`: `spring-boot-starter-mail`(Jakarta Mail)로 `config` 의 host/port/from + `secret_ref` 비밀번호로 발송. **새 의존성 1개**(`apps/backend/build.gradle`) 추가가 필요하다.
- **비밀값은 절대 로그·응답·DB 에 남기지 않는다.** 실패 `detail` 에도 URL/비밀번호를 넣지 않는다(호스트 정도만).

### 3.3 오류 규칙 (400 / 403 / 404 / 409)

| 경우 | 상태 | 본문 예 (기존 GlobalExceptionHandler 형식) |
| --- | --- | --- |
| 본문 검증 실패(필수값·범위) | **400** | `amountUsd 는 0.01 이상이어야 합니다` |
| 알 수 없는 enum/쿼리(`bucket=BOGUS`, `kind`, `eventType`) | **400** | `알 수 없는 버킷입니다: BOGUS` |
| `config` 에 비밀값 키(`url`/`password`/`token`) | **400** | `비밀값은 config 가 아니라 secret_ref(환경변수 이름)로 지정하세요` |
| 서버 egress 꺼짐(`notifications.egress-enabled=false`)의 발송·테스트 | **403** | `외부 알림 발송이 꺼져 있습니다` |
| 없는 프로젝트/채널/규칙 | **404** | `Project 99 not found` / `NotificationChannel 7 not found` |
| 예산 미설정 조회(`GET .../budget`) | **404** | `Budget for project 6 not found` |
| 규칙 중복(채널·이벤트·범위) | **409** | `같은 채널·이벤트·범위 규칙이 이미 있습니다` |
| 테스트/발송 시 `secret_ref` 환경변수 없음 | **409** | `자격증명(환경변수 NOTIFY_SLACK_URL)이 설정되지 않았습니다` |
| 유효한데 결과 0건(목록·사용량) | **200** | `[]` / 0 합계 (404 아님) |

- 4xx(검증/입력)는 내부 오류 링버퍼(`ServerErrorLog`)에 담지 않는다(`GlobalExceptionHandler` 기존 규칙).
- **403 을 permission profile 로 만들지 않는다**: 알림은 앱 전역 설정이라 에이전트의 `PermissionProfile`(예: `EXTERNAL_NETWORK_ACCESS`)을 참조할 대상이 없다. 403 은 **서버 설정(egress off)** 하나로 한정한다 — `PermissionService` 를 새로 엮지 않는다.
### 3.4 실시간 이벤트 (`GET /events/stream` 규칙)

전역 스트림은 **페이로드가 아니라 알림**이다. 새 타입 2개를 `event/dto/EventTypes` 에 추가하고 `EventPublisher` 에 메서드를 더한다(발행은 서비스 계층에서만):

| 타입 | 언제 | 싣는 식별자 | 프론트가 다시 읽을 조각 |
| --- | --- | --- | --- |
| `budget.changed` | 예산 생성/수정/삭제, 80% 경고 발행 | `projectId`(예산 프로젝트) | 그 프로젝트의 사용량·예산(`GET /executions/usage`·`GET /projects/{id}/budget`) |
| `notification.changed` | 채널·규칙 생성/수정/삭제 | `projectId`(프로젝트 규칙이면 그 id, 전역이면 null) | 채널 목록 + 규칙 목록(`GET /notifications/channels`·`/rules`) |

```java
// event/service/EventPublisher 에 추가
public void budgetChanged(Long projectId) {
    publish(EventTypes.BUDGET_CHANGED, projectId, null, null);
}
public void notificationChanged(Long projectId) {
    publish(EventTypes.NOTIFICATION_CHANGED, projectId, null, null);
}
```

- `event/dto/EventTypes` 에 `BUDGET_CHANGED = "budget.changed"`, `NOTIFICATION_CHANGED = "notification.changed"` 추가. `DataChangedEvent` 구조는 **바꾸지 않는다**(기존 4필드 그대로).
- 프론트 `store/AgentDockStore.tsx` 의 `applyEvent` switch 에 `budget.changed`(해당 프로젝트 슬라이스 재조회), `notification.changed`(채널·규칙 재조회) case 추가. **주기 전체 폴링 금지**(안전망 폴링만).
## 4. 엔티티 변경점 (ddl-auto=validate — 어긋나면 기동 실패)

신규 엔티티 4개. 기존 `Execution`·`Project` 엔티티는 **손대지 않는다**(집계는 읽기 전용). 매핑은 마이그레이션과 **글자 단위로 일치**해야 한다(§2). enum 은 `@Enumerated(EnumType.STRING)`, JSONB 는 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`(기존 `Execution.changedFiles` 패턴).

### `budget/domain/ProjectBudget.java` — `@Table(name = "project_budget")`

| Java 필드 | 타입 | 컬럼 |
| --- | --- | --- |
| `id` | Long, `@Id @GeneratedValue(IDENTITY)` | `id` |
| `projectId` | Long, `@Column(name="project_id", nullable=false)` | `project_id` |
| `monthlyAmountUsd` | BigDecimal, `@Column(name="monthly_amount_usd", precision=12, scale=2, nullable=false)` | `monthly_amount_usd` |
| `warnThresholdPercent` | int, `@Column(name="warn_threshold_percent", nullable=false)` | `warn_threshold_percent` |
| `notifiedMonth` | String, `@Column(name="notified_month", length=7)` | `notified_month` |
| `notifiedAt` | Instant | `notified_at` |
| `createdAt`/`updatedAt` | Instant, `@CreationTimestamp`/`@UpdateTimestamp` | `created_at`/`updated_at` |

- 리포지토리: `Optional<ProjectBudget> findByProjectId(Long)`.

### `notification/domain/NotificationChannel.java` — `@Table(name = "notification_channel")`

| Java 필드 | 타입 | 컬럼 |
| --- | --- | --- |
| `id` | Long IDENTITY | `id` |
| `kind` | `NotificationChannelKind` `@Enumerated(STRING)` `@Column(nullable=false, length=16)` | `kind` |
| `name` | String `@Column(nullable=false)` | `name` |
| `enabled` | boolean `@Column(nullable=false)` | `enabled` |
| `config` | `Map<String,Object>` `@JdbcTypeCode(JSON)` `@Column(columnDefinition="jsonb", nullable=false)` | `config` |
| `secretRef` | String `@Column(name="secret_ref", length=128)` | `secret_ref` |
| `createdAt`/`updatedAt` | Instant | `created_at`/`updated_at` |
### `notification/domain/NotificationRule.java` — `@Table(name = "notification_rule")`

| Java 필드 | 타입 | 컬럼 |
| --- | --- | --- |
| `id` | Long IDENTITY | `id` |
| `channelId` | Long `@Column(name="channel_id", nullable=false)` | `channel_id` |
| `eventType` | `NotificationEventType` `@Enumerated(STRING)` `@Column(name="event_type", nullable=false, length=48)` | `event_type` |
| `projectId` | Long `@Column(name="project_id")`(nullable) | `project_id` |
| `enabled` | boolean `@Column(nullable=false)` | `enabled` |
| `createdAt`/`updatedAt` | Instant | `created_at`/`updated_at` |

### `notification/domain/NotificationDelivery.java` — `@Table(name = "notification_delivery")`

`id`(IDENTITY) · `channelId`(`channel_id`, NOT NULL) · `ruleId`(`rule_id`, nullable) · `eventType`(`event_type`, length 48, nullable — enum 이 아니라 `String`) · `projectId`(nullable) · `status`(`DeliveryStatus` `SENT`/`FAILED`, STRING) · `httpStatus`(`http_status`) · `detail`(`@Column(columnDefinition="text")`) · `createdAt`.

**주의**: `NotificationRule` 은 `channelId`/`projectId` 를 **shadow FK 필드**로 갖는다(관계 매핑을 만들지 않는다 — `Execution.taskId` 패턴). 관계 엔티티를 매핑하면 Jackson 순환·지연로딩 문제가 생긴다. 응답은 항상 `record` 로 평평하게 낸다.
## 5. 다른 팀과의 경계 · 충돌 경고 (공유 노트에 함께 남김)

- **Flyway 번호 충돌**: 이 계약은 G/H=**V26~V28** 을 확정한다. C/D/E 설계(실행 26)는 V23~V25 다. 그런데 백엔드 API 담당 지시(`.agentdock/prompts/247.md`)는 "V24·V25·V26" 을 언급해 **V26 이 겹친다**. Flyway 는 버전 중복 시 기동이 실패하므로, **백엔드는 이 계약의 V26~V28 을 G/H 전용으로 예약**하고 V24/V25 만 쓰거나, V26 을 비운다. 병합 전에 반드시 번호를 대조한다.
- **A 계약 불변**: `GET /executions`·`ExecutionSummaryResponse`·`GET /executions/summary`·`ExecutionStatsResponse` 는 **한 글자도 바꾸지 않는다**. 기간 사용량은 새 `GET /executions/usage` 다.
- **자격증명 원칙**: 알림 비밀값은 DB 저장 금지, `secret_ref`(환경변수 이름)만 저장. 화면은 "설정됨/미설정"(`secretResolved`)으로만 표시한다.
- **D(예약)와 공유**: 예약 실행 실패 알림을 붙이려면 `NotificationEventType` 에 `SCHEDULE_FAILED` 를 D 계약과 합의해 추가한다(이 계약은 아직 넣지 않는다 — D 미확정).

## 6. 한 커밋에서 끝낼 범위 (커밋 단위 — 추가 전용)

전부 **추가 전용(additive)** 이다 — 백엔드를 먼저 올려도 기존 화면이 깨지지 않는다(기존 엔드포인트·DTO·테이블을 건드리지 않는다).

- **Commit G — 비용·예산**: `V26` + `budget/domain|repository|service|controller|dto` + `execution`(`UsageResponse`·`ExecutionService.usage`·repo 프로젝션·`GET /executions/usage`) + `EventTypes/EventPublisher`(`budget.changed`) + `application.yaml`(budget) + 테스트(기간 버킷·null 합계·404·level 계산).
- **Commit H — 알림**: `V27`·`V28` + `notification/domain|repository|service|controller|dto` + `EventTypes/EventPublisher`(`notification.changed`) + `build.gradle`(mail) + `application.yaml`(notifications) + 발송 트리거 배선(ExecutionService·DelegationService·BudgetService) + 테스트(CRUD·중복 409·secret 409·egress 403·테스트 발송 기록).
- **하지 않음**: `ExecutionStatsResponse` 수정, 에이전트 permission 연동, 예산 초과 차단/승인 흐름(253 결정 뒤), D 예약 이벤트.
- 검증: `.\gradlew.bat test` · `npm run build --workspace=apps/frontend` · 8081/3031 실측(집계 기간·예산 upsert·채널/규칙·이벤트 후 재조회).
## 7. 프론트 소비 요약 (데이터 19 · 화면 18 이 바로 쓸 것)

- 엔드포인트 클라이언트(`lib/api.ts`): `getUsage({projectId,from,to,bucket})` · `getBudget(projectId)`·`setBudget(projectId,body)`·`deleteBudget(projectId)` · `listNotificationChannels()`·`createNotificationChannel()`·`updateNotificationChannel()`·`deleteNotificationChannel()`·`testNotificationChannel(id)` · `listNotificationRules()`·`createNotificationRule()`·`updateNotificationRule()`·`deleteNotificationRule()` · `listNotificationDeliveries(query)`.
- 실시간: `budget.changed`·`notification.changed` 를 받으면 위 조각만 재조회(주기 전체 폴링 금지).
- 스토어 슬라이스(이름은 데이터 19 가 확정, 제안): `usage`·`budget`·`notificationChannels`·`notificationRules`·`notificationDeliveries` + 액션 `loadUsage`·`loadBudget`·`saveBudget`·`loadNotificationChannels`·`saveNotificationChannel`·`loadNotificationRules`·`saveNotificationRule`·`testNotificationChannel`.
- 80% 경고 UI: `GET /executions/usage` 의 `budget.level`(`OK`/`WARN`/`EXCEEDED`)·`percent` 로 표시. 별도 API 를 부르지 않는다.