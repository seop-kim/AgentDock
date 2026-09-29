# Agent 연결 설정 재구성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Providers 화면을 "Agent 연결 설정"으로 재구성한다: Provider 4종 시드, 카드형 화면 + Provider 추가 모달, 웹 로그인 패널, Provider 논리 삭제와 Agent 재할당, 연결 상태에 종속된 Agent 실행 가드.

**Architecture:** 백엔드는 `provider` 모듈에 논리 삭제(`deleted_at`)와 Connection 1:1 규칙을 넣고, Agent 사용 가능 여부는 `AgentAvailability` 한 곳에서 파생 계산해 Agent 응답과 `ExecutionService` 가드가 같이 쓴다. 로그인은 `provider.login` 서브패키지에서 `LoginProcess`(파이프 구현) + 세션 서비스(출력 버퍼 재생, SSE)로 제공한다. 프론트는 카드/모달/로그인 패널 컴포넌트로 분리한다.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Data JPA + QueryDSL, Flyway, JUnit 5 + Mockito + AssertJ / Vite + React 18 + TypeScript + CSS Modules

**Spec:** [`docs/superpowers/specs/2026-09-29-agent-connection-settings-design.md`](../specs/2026-09-29-agent-connection-settings-design.md)

## Global Constraints

- Flyway는 새 버전 파일(`V4__...`)만 추가하고 기존 마이그레이션은 수정하지 않는다.
- `spring.jpa.open-in-view: false`: 서비스에서 LAZY 관계를 읽지 말고 fetch join 조회를 쓴다. 오래 걸리는 작업(CLI probe/로그인)은 `@Transactional` 로 감싸지 않는다.
- POST 는 201(`@ResponseStatus(HttpStatus.CREATED)`), 삭제/입력 전달은 204. 응답은 엔티티가 아니라 `record` Response DTO 다. 요청은 `record` + Jakarta Validation.
- shadow FK 필드(`insertable = false, updatable = false`)는 저장 직후 비어 있다. 응답을 만들 때 관계에서 보완하거나 저장 후 새로 조회한다.
- 로그인 명령은 Provider 별 **서버 고정값**이다. 사용자 입력으로 명령을 만들지 않는다.
- Codex/Gemini/Command Code 로그인 명령은 CLI 가 설치돼 있지 않으므로 추측해서 넣지 않는다("로그인 미지원").
- 에이전트가 검증할 때는 백엔드 8081 / 프론트 3031 만 쓰고 끝나면 즉시 종료한다(사용자 개발 포트 8080/3030 사용 금지).
- 커밋은 Conventional Commits(`feat: ...`, `fix: ...`), 한 커밋은 하나의 논리 단위. **`Co-Authored-By` 트레일러를 붙이지 않는다.** 한국어 허용.
- 화면 문구는 한국어, 코드/식별자는 영어.
- 구조/컨벤션이 바뀌므로 `agents/CONVENTIONS.md` 와 `agents/HANDOVER.md` 를 함께 갱신한다(Task 9).

## Review Focus

1. **삭제된 key 재등록**: Provider 를 논리 삭제한 뒤 같은 key 로 다시 등록할 수 있어야 한다(부분 유니크 인덱스). 삭제되지 않은 같은 key 는 409. → Task 1(스키마 확인), Task 3(테스트)
2. **삭제된 Provider 로 재할당/생성 시도**: 404 여야 하고 Agent 는 바뀌지 않는다. → Task 4
3. **Provider 삭제 시 FK 위반 없음**: Connection 을 지우기 전에 `agent.connection_id` 를 먼저 NULL 로 만든다(순서 검증). → Task 3
4. **로그인 SSE 구독이 프로세스 시작보다 늦어도 출력이 유실되지 않음**(URL 을 놓치면 로그인 불가). → Task 5
5. **CLI 바이너리가 없을 때**: 세션이 예외로 끝나지 않고 안내 출력 + `exit(-1)` 이벤트로 끝난다. → Task 5

## 공통 준비 (모든 Gradle 명령 전에)

```powershell
$env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd C:\Users\chey.kim\Documents\GitHub\AgentDock\apps\backend
```

경로 약칭: `B` = `apps/backend/src/main/java/com/agent/dock`, `T` = `apps/backend/src/test/java/com/agent/dock`, `F` = `apps/frontend/src`.

단위 테스트는 Mockito 로 저장소를 대체하므로 DB 없이 돈다. `gradlew test` 전체를 돌리면 `AgentDockApplicationTests`(컨텍스트 로딩)가 실제 DB에 붙으니, 아래 명령은 항상 `--tests` 로 대상을 좁힌다.

---

### Task 1: 논리 삭제 스키마 + 4종 시드

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V4__provider_soft_delete_and_seed.sql`
- Modify: `apps/backend/build.gradle`
- Modify: `B/provider/AiProvider.java`
- Modify: `B/provider/AiProviderRepository.java`
- Modify: `B/provider/AiConnectionRepository.java`
- Modify: `B/agent/AgentRepository.java`

**Interfaces:**
- Produces: `AiProvider.getDeletedAt()/setDeletedAt(Instant)`; `AiProviderRepository.findByDeletedAtIsNullOrderByNameAsc()`, `findByIdAndDeletedAtIsNull(Long)`, `existsByKeyAndDeletedAtIsNull(ProviderKey)`; `AiConnectionRepository.findByProviderIdIn(Collection<Long>)`, `existsByProviderId(Long)`, `deleteByProviderId(Long)`; `AgentRepository.detachConnectionsOfProvider(Long)`, `detachConnection(Long)` (둘 다 `int` 반환).

- [ ] **Step 1: 테스트 의존성 추가**

`apps/backend/build.gradle` 의 `dependencies` 블록에서 `testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'` 바로 위에 추가한다(Mockito/AssertJ/JUnit params 확보).

```groovy
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
```

- [ ] **Step 2: 마이그레이션 작성**

`apps/backend/src/main/resources/db/migration/V4__provider_soft_delete_and_seed.sql`:

```sql
-- Provider 논리 삭제
ALTER TABLE ai_provider ADD COLUMN deleted_at TIMESTAMPTZ;

-- key 유일성은 삭제되지 않은 행에만 적용한다(삭제한 key 를 다시 등록할 수 있게)
ALTER TABLE ai_provider DROP CONSTRAINT ai_provider_key_key;
CREATE UNIQUE INDEX ai_provider_key_active ON ai_provider (key) WHERE deleted_at IS NULL;

-- 4종 Provider 시드: 같은 key 의 행이 하나도 없을 때만 넣는다
INSERT INTO ai_provider (key, name)
SELECT v.key, v.name
FROM (VALUES
    ('CLAUDE_CODE', 'Claude Code'),
    ('CODEX', 'Codex'),
    ('COMMAND_CODE', 'Command Code'),
    ('GEMINI', 'Gemini')
) AS v(key, name)
WHERE NOT EXISTS (SELECT 1 FROM ai_provider p WHERE p.key = v.key);

-- Provider 당 Connection 1개: 없는 Provider 에만 만든다
INSERT INTO ai_connection (provider_id)
SELECT p.id
FROM ai_provider p
WHERE p.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM ai_connection c WHERE c.provider_id = p.id);
```

- [ ] **Step 3: 엔티티 수정**

`B/provider/AiProvider.java`: key 컬럼의 `unique = true` 를 제거하고 `deletedAt` 필드를 추가한다.

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderKey key;
```

`updatedAt` 필드 아래에 추가:

```java
    /** 논리 삭제 시각. null 이면 사용 가능한 Provider. */
    private Instant deletedAt;
```

- [ ] **Step 4: 저장소 메서드 추가**

`B/provider/AiProviderRepository.java` 전체를 다음으로 교체한다(기존 `findAllByOrderByNameAsc` 는 Task 3에서 서비스가 새 메서드로 옮겨 간 뒤 제거되므로 지금은 남긴다).

```java
package com.agent.dock.provider;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    @EntityGraph(attributePaths = "connections")
    List<AiProvider> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "connections")
    List<AiProvider> findByDeletedAtIsNullOrderByNameAsc();

    Optional<AiProvider> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByKeyAndDeletedAtIsNull(ProviderKey key);
}
```

`B/provider/AiConnectionRepository.java` 전체를 다음으로 교체한다.

```java
package com.agent.dock.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiConnectionRepository extends JpaRepository<AiConnection, Long> {
    List<AiConnection> findByProviderId(Long providerId);

    List<AiConnection> findByProviderIdIn(Collection<Long> providerIds);

    boolean existsByProviderId(Long providerId);

    void deleteByProviderId(Long providerId);

    @Query("select c from AiConnection c join fetch c.provider where c.id = :id")
    Optional<AiConnection> findWithProvider(@Param("id") Long id);
}
```

`B/agent/AgentRepository.java` 전체를 다음으로 교체한다.

```java
package com.agent.dock.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRepository extends JpaRepository<Agent, Long>, AgentRepositoryCustom {

    /** Provider 의 Connection 을 삭제하기 전에 Agent 의 참조를 끊는다(FK 위반 방지). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Agent a set a.connection = null "
            + "where a.connection.id in (select c.id from AiConnection c where c.provider.id = :providerId)")
    int detachConnectionsOfProvider(@Param("providerId") Long providerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Agent a set a.connection = null where a.connection.id = :connectionId")
    int detachConnection(@Param("connectionId") Long connectionId);
}
```

- [ ] **Step 5: 컴파일 확인**

Run (공통 준비 후): `.\gradlew.bat compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`. (`spring-boot-starter-test` 를 받지 못하면 의존성 이름을 확인한다.)

- [ ] **Step 6: 백엔드를 8081 에 띄워 마이그레이션 적용 확인**

```powershell
.\gradlew.bat bootRun --args=--server.port=8081
```

백그라운드로 실행하고 로그에서 `Started AgentDockApplication` 을 확인한다(**BUILD SUCCESSFUL 만으로 판단하지 말 것**). 그다음:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -U postgres -d AGENT_DOCK -c "SELECT p.key, p.name, p.deleted_at, count(c.id) AS connections FROM ai_provider p LEFT JOIN ai_connection c ON c.provider_id = p.id GROUP BY p.id ORDER BY p.key"
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -U postgres -d AGENT_DOCK -c "\d ai_provider"
```

Expected: 4 행(CLAUDE_CODE, CODEX, COMMAND_CODE, GEMINI), 각 `connections` ≥ 1, `deleted_at` 비어 있음. `\d ai_provider` 의 Indexes 에 `ai_provider_key_active ... WHERE deleted_at IS NULL` 이 있고 `ai_provider_key_key` 는 없다.

- [ ] **Step 7: 서버 종료**

```powershell
Get-NetTCPConnection -LocalPort 8081 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

- [ ] **Step 8: Commit**

```bash
git add apps/backend/build.gradle apps/backend/src/main/resources/db/migration/V4__provider_soft_delete_and_seed.sql apps/backend/src/main/java/com/agent/dock/provider apps/backend/src/main/java/com/agent/dock/agent/AgentRepository.java
git commit -m "feat: Provider 논리 삭제 스키마와 4종 시드 추가"
```

---

### Task 2: AgentAvailability + 실행 가드

**Files:**
- Create: `B/agent/AgentAvailability.java`
- Create: `T/agent/AgentAvailabilityTest.java`
- Create: `T/execution/ExecutionServiceGuardTest.java`
- Modify: `B/execution/ExecutionService.java`

**Interfaces:**
- Consumes: `AiProvider.getDeletedAt()`, `AiConnectionRepository.findByProviderId(Long)` (Task 1)
- Produces: `AgentAvailability.evaluate(boolean, List<ConnectionStatus>)`, `AgentAvailability.evaluate(AiProvider, List<AiConnection>)` → `AgentAvailability.Result(boolean available, Reason reason)`; `Result.message()`; `Result.OK`; `enum Reason { PROVIDER_DELETED, CONNECTION_NOT_CONNECTED }`

- [ ] **Step 1: AgentAvailability 실패하는 테스트 작성**

`T/agent/AgentAvailabilityTest.java`:

```java
package com.agent.dock.agent;

import com.agent.dock.provider.ConnectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAvailabilityTest {

    @Test
    void deletedProviderIsUnavailableEvenWhenConnected() {
        var result = AgentAvailability.evaluate(true, List.of(ConnectionStatus.CONNECTED));

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.PROVIDER_DELETED);
    }

    @Test
    void noConnectionIsUnavailable() {
        var result = AgentAvailability.evaluate(false, List.of());

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.CONNECTION_NOT_CONNECTED);
    }

    @ParameterizedTest
    @EnumSource(value = ConnectionStatus.class, names = {"DISCONNECTED", "ERROR"})
    void notConnectedStatusIsUnavailable(ConnectionStatus status) {
        var result = AgentAvailability.evaluate(false, List.of(status));

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AgentAvailability.Reason.CONNECTION_NOT_CONNECTED);
    }

    @Test
    void connectedIsAvailable() {
        var result = AgentAvailability.evaluate(false, List.of(ConnectionStatus.CONNECTED));

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void anyConnectedAmongLegacyMultipleConnectionsIsAvailable() {
        var result = AgentAvailability.evaluate(false, List.of(ConnectionStatus.ERROR, ConnectionStatus.CONNECTED));

        assertThat(result.available()).isTrue();
    }

    @Test
    void messageDiffersPerReason() {
        assertThat(AgentAvailability.evaluate(true, List.of()).message()).contains("deleted");
        assertThat(AgentAvailability.evaluate(false, List.of()).message()).contains("not CONNECTED");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.agent.AgentAvailabilityTest"`
Expected: 컴파일 오류(`AgentAvailability` 없음).

- [ ] **Step 3: 구현**

`B/agent/AgentAvailability.java`:

```java
package com.agent.dock.agent;

import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;

import java.util.List;

/**
 * Agent 를 지금 실행할 수 있는지 판단하는 유일한 규칙. 저장하지 않고 매번 파생한다.
 * Agent 응답(화면 표시)과 실행 가드(ExecutionService)가 같은 규칙을 쓴다.
 */
public final class AgentAvailability {

    public enum Reason { PROVIDER_DELETED, CONNECTION_NOT_CONNECTED }

    public record Result(boolean available, Reason reason) {
        public static final Result OK = new Result(true, null);

        public String message() {
            if (reason == null) {
                return "Agent is available";
            }
            return switch (reason) {
                case PROVIDER_DELETED -> "Provider was deleted; assign another provider to this agent";
                case CONNECTION_NOT_CONNECTED -> "Provider connection is not CONNECTED; check it in Agent 연결 설정";
            };
        }
    }

    private AgentAvailability() {
    }

    public static Result evaluate(boolean providerDeleted, List<ConnectionStatus> connectionStatuses) {
        if (providerDeleted) {
            return new Result(false, Reason.PROVIDER_DELETED);
        }
        boolean connected = connectionStatuses.stream().anyMatch(status -> status == ConnectionStatus.CONNECTED);
        return connected ? Result.OK : new Result(false, Reason.CONNECTION_NOT_CONNECTED);
    }

    public static Result evaluate(AiProvider provider, List<AiConnection> connections) {
        return evaluate(provider.getDeletedAt() != null, connections.stream().map(AiConnection::getStatus).toList());
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.agent.AgentAvailabilityTest"`
Expected: PASS (6 tests + 파라미터 2건).

- [ ] **Step 5: 실행 가드 실패하는 테스트 작성**

`T/execution/ExecutionServiceGuardTest.java`:

```java
package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ConflictException;
import com.agent.dock.permission.PermissionAction;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionService;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.runtime.RuntimeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceGuardTest {
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository logRepository;
    @Mock AgentRepository agentRepository;
    @Mock ProjectRepository projectRepository;
    @Mock RuntimeRegistry runtimeRegistry;
    @Mock PermissionService permissionService;
    @Mock AiConnectionRepository connectionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ExecutionService service;

    private AiProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AiProvider();
        provider.setId(1L);
        Agent agent = new Agent();
        agent.setProvider(provider);
        agent.setPermissionProfile(new PermissionProfile());
        when(agentRepository.findByIdWithRelations(5L)).thenReturn(Optional.of(agent));
        when(projectRepository.findWithWorkspace(10L)).thenReturn(Optional.of(new Project()));
        when(permissionService.isAllowed(any(), eq(PermissionAction.TERMINAL_EXECUTE))).thenReturn(true);
    }

    @Test
    void rejectsWhenProviderDeleted() {
        provider.setDeletedAt(Instant.now());

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deleted");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenNoConnection() {
        when(connectionRepository.findByProviderId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not CONNECTED");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenConnectionInError() {
        AiConnection connection = new AiConnection();
        connection.setStatus(ConnectionStatus.ERROR);
        when(connectionRepository.findByProviderId(1L)).thenReturn(List.of(connection));

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not CONNECTED");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }
}
```

- [ ] **Step 6: 실패 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.execution.ExecutionServiceGuardTest"`
Expected: FAIL — `ExecutionService` 에 `AiConnectionRepository` 가 없어 `connectionRepository` 주입이 안 되고, 가드가 없어 예외가 안 난다(컴파일은 되지만 assertion 실패).

- [ ] **Step 7: 가드 구현**

`B/execution/ExecutionService.java`: import 추가

```java
import com.agent.dock.agent.AgentAvailability;
import com.agent.dock.common.ConflictException;
import com.agent.dock.provider.AiConnectionRepository;
```

필드 추가(`private final PermissionService permissionService;` 아래):

```java
    private final AiConnectionRepository connectionRepository;
```

`create(...)` 의 권한 검사 블록 바로 뒤에 가드를 추가한다.

```java
        if (!permissionService.isAllowed(agent.getPermissionProfile(), PermissionAction.TERMINAL_EXECUTE)) {
            throw new ForbiddenException("Agent permission profile does not allow TERMINAL_EXECUTE");
        }

        // Provider 가 삭제됐거나 연결이 CONNECTED 가 아니면 Runtime 을 호출하지 않고 즉시 거부한다(새 실행만 차단).
        var availability = AgentAvailability.evaluate(agent.getProvider(),
                connectionRepository.findByProviderId(agent.getProvider().getId()));
        if (!availability.available()) {
            throw new ConflictException(availability.message());
        }
```

- [ ] **Step 8: 통과 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.execution.ExecutionServiceGuardTest" --tests "com.agent.dock.agent.AgentAvailabilityTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add apps/backend/src/main/java/com/agent/dock/agent/AgentAvailability.java apps/backend/src/main/java/com/agent/dock/execution/ExecutionService.java apps/backend/src/test
git commit -m "feat: Provider 삭제/연결 상태에 따른 Agent 실행 가드 추가"
```

---

### Task 3: Provider·Connection 서비스와 API

**Files:**
- Modify: `B/provider/AiProviderService.java`
- Modify: `B/provider/AiProviderRepository.java` (기존 `findAllByOrderByNameAsc` 제거)
- Modify: `B/provider/AiConnectionService.java`
- Modify: `B/provider/AiProviderController.java`
- Modify: `B/provider/AiConnectionController.java`
- Test: `T/provider/AiProviderServiceTest.java`, `T/provider/AiConnectionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 의 저장소 메서드
- Produces: `AiProviderService.delete(Long)`, `createProviderConnection(Long)`; `AiConnectionService.delete(Long)`; `DELETE /ai-providers/{id}`(204), `POST /ai-providers/{id}/connection`(201), `DELETE /ai-connections/{id}`(204)

- [ ] **Step 1: 실패하는 테스트 작성 (Provider 서비스)**

`T/provider/AiProviderServiceTest.java`:

```java
package com.agent.dock.provider;

import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderServiceTest {
    @Mock AiProviderRepository providerRepository;
    @Mock AiConnectionRepository connectionRepository;
    @Mock AgentRepository agentRepository;
    @InjectMocks AiProviderService service;

    @Test
    void createRejectsDuplicateActiveKey() {
        when(providerRepository.existsByKeyAndDeletedAtIsNull(ProviderKey.CODEX)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateAiProviderRequest(ProviderKey.CODEX, "Codex", null)))
                .isInstanceOf(ConflictException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void createAllowsKeyWhoseOnlyOtherRowIsDeleted() {
        // 삭제된 행은 existsByKeyAndDeletedAtIsNull 에서 제외되므로 false 로 돌아온다
        when(providerRepository.existsByKeyAndDeletedAtIsNull(ProviderKey.CODEX)).thenReturn(false);
        when(providerRepository.save(any(AiProvider.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionRepository.save(any(AiConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateAiProviderRequest(ProviderKey.CODEX, "Codex", null));

        assertThat(response.key()).isEqualTo(ProviderKey.CODEX);
    }

    @Test
    void createAlsoCreatesTheSingleConnection() {
        when(providerRepository.save(any(AiProvider.class))).thenAnswer(inv -> inv.getArgument(0));
        when(connectionRepository.save(any(AiConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new CreateAiProviderRequest(ProviderKey.GEMINI, "Gemini", null));

        ArgumentCaptor<AiConnection> captor = ArgumentCaptor.forClass(AiConnection.class);
        verify(connectionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(response.connections()).hasSize(1);
    }

    @Test
    void deleteDetachesAgentsBeforeRemovingConnectionsThenSoftDeletes() {
        AiProvider provider = new AiProvider();
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(provider));

        service.delete(1L);

        InOrder order = inOrder(agentRepository, connectionRepository, providerRepository);
        order.verify(agentRepository).detachConnectionsOfProvider(1L);
        order.verify(connectionRepository).deleteByProviderId(1L);
        order.verify(providerRepository).save(provider);
        assertThat(provider.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteOfMissingOrAlreadyDeletedProviderIsNotFound() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).detachConnectionsOfProvider(any());
    }

    @Test
    void createProviderConnectionRejectsWhenOneExists() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(new AiProvider()));
        when(connectionRepository.existsByProviderId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.createProviderConnection(1L)).isInstanceOf(ConflictException.class);
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void createProviderConnectionCreatesOneWhenMissing() {
        when(providerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(new AiProvider()));
        when(connectionRepository.existsByProviderId(1L)).thenReturn(false);
        when(connectionRepository.save(any(AiConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createProviderConnection(1L);

        assertThat(response.status()).isEqualTo(ConnectionStatus.DISCONNECTED);
    }
}
```

`T/provider/AiConnectionServiceTest.java`:

```java
package com.agent.dock.provider;

import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConnectionServiceTest {
    @Mock AiConnectionRepository connectionRepository;
    @Mock ProbeRegistry probeRegistry;
    @Mock AgentRepository agentRepository;
    @InjectMocks AiConnectionService service;

    @Test
    void deleteDetachesAgentsThenDeletes() {
        when(connectionRepository.existsById(3L)).thenReturn(true);

        service.delete(3L);

        InOrder order = inOrder(agentRepository, connectionRepository);
        order.verify(agentRepository).detachConnection(3L);
        order.verify(connectionRepository).deleteById(3L);
    }

    @Test
    void deleteOfMissingConnectionIsNotFound() {
        when(connectionRepository.existsById(3L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).detachConnection(any());
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.provider.AiProviderServiceTest" --tests "com.agent.dock.provider.AiConnectionServiceTest"`
Expected: 컴파일 오류(`delete`, `createProviderConnection`, 서비스의 `AgentRepository` 필드 없음).

- [ ] **Step 3: AiProviderService 구현**

`B/provider/AiProviderService.java` 전체를 교체한다.

```java
package com.agent.dock.provider;

import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderService {
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;
    private final AgentRepository agentRepository;

    /** 논리 삭제되지 않은 Provider 만 반환한다. */
    public List<AiProviderResponse> findAll() {
        return providerRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(AiProviderResponse::from).toList();
    }

    /** Provider 를 만들고 Connection(Provider 당 1개)을 함께 만든다. */
    @Transactional
    public AiProviderResponse create(CreateAiProviderRequest request) {
        if (providerRepository.existsByKeyAndDeletedAtIsNull(request.key())) {
            throw new ConflictException("Provider already registered: " + request.key());
        }
        AiProvider provider = new AiProvider();
        provider.setKey(request.key());
        provider.setName(request.name());
        provider.setCapabilities(request.capabilities() != null ? request.capabilities() : new HashMap<>());
        AiProvider saved = providerRepository.save(provider);

        AiConnection connection = new AiConnection();
        connection.setProvider(saved);
        saved.setConnections(List.of(connectionRepository.save(connection)));
        return AiProviderResponse.from(saved);
    }

    /**
     * 논리 삭제. Agent 는 그대로 두고(사용 불가가 된다), Connection 은 지운다.
     * Connection 을 지우기 전에 Agent 의 connection_id 를 먼저 끊어 FK 위반을 피한다.
     */
    @Transactional
    public void delete(Long id) {
        AiProvider provider = findActive(id);
        agentRepository.detachConnectionsOfProvider(id);
        connectionRepository.deleteByProviderId(id);
        provider.setDeletedAt(Instant.now());
        providerRepository.save(provider);
    }

    /** 삭제된 Connection 을 다시 만든다("연결 추가"). Provider 당 1개만 허용한다. */
    public AiConnectionResponse createProviderConnection(Long providerId) {
        AiProvider provider = findActive(providerId);
        if (connectionRepository.existsByProviderId(providerId)) {
            throw new ConflictException("AiProvider %d already has a connection".formatted(providerId));
        }
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    public AiConnectionResponse createConnection(CreateAiConnectionRequest request) {
        AiProvider provider = findActive(request.providerId());
        if (connectionRepository.existsByProviderId(request.providerId())) {
            throw new ConflictException("AiProvider %d already has a connection".formatted(request.providerId()));
        }
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        connection.setAccountName(request.accountName());
        connection.setCredentialReference(request.credentialReference());
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    public List<AiConnectionResponse> listConnections(Long providerId) {
        return connectionRepository.findByProviderId(providerId).stream().map(AiConnectionResponse::from).toList();
    }

    private AiProvider findActive(Long id) {
        return providerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(id)));
    }
}
```

`B/provider/AiProviderRepository.java` 에서 더 이상 쓰지 않는 `findAllByOrderByNameAsc()` (와 그 `@EntityGraph`) 를 제거한다.

- [ ] **Step 4: AiConnectionService 에 delete 추가**

`B/provider/AiConnectionService.java`: import 추가

```java
import com.agent.dock.agent.AgentRepository;
import org.springframework.transaction.annotation.Transactional;
```

필드 추가(`private final ProbeRegistry probeRegistry;` 아래):

```java
    private final AgentRepository agentRepository;
```

메서드 추가(클래스 맨 끝):

```java
    /** Connection 을 지운다. 참조하던 Agent 는 연결이 끊긴 상태가 되어 실행 가드에 막힌다. */
    @Transactional
    public void delete(Long connectionId) {
        if (!connectionRepository.existsById(connectionId)) {
            throw new NotFoundException("AiConnection %d not found".formatted(connectionId));
        }
        agentRepository.detachConnection(connectionId);
        connectionRepository.deleteById(connectionId);
    }
```

(`check` 는 오래 걸리는 probe 를 포함하므로 `@Transactional` 을 붙이지 않는다.)

- [ ] **Step 5: 컨트롤러 엔드포인트 추가**

`B/provider/AiProviderController.java` 에 추가(`createConnection` 아래):

```java
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/connection")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConnectionResponse createProviderConnection(@PathVariable Long id) {
        return service.createProviderConnection(id);
    }
```

`B/provider/AiConnectionController.java` 에 추가:

```java
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
```

- [ ] **Step 6: 통과 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.provider.AiProviderServiceTest" --tests "com.agent.dock.provider.AiConnectionServiceTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src
git commit -m "feat: Provider 논리 삭제와 Connection 삭제/재생성 API 추가"
```

---

### Task 4: Agent 사용 가능 여부 응답 + Provider 재할당

**Files:**
- Create: `B/agent/AssignProviderRequest.java`
- Modify: `B/agent/AgentResponse.java`
- Modify: `B/agent/AgentService.java`
- Modify: `B/agent/AgentController.java`
- Test: `T/agent/AgentServiceTest.java`

**Interfaces:**
- Consumes: `AgentAvailability` (Task 2), `AiProviderRepository.findByIdAndDeletedAtIsNull`, `AiConnectionRepository.findByProviderIdIn/findByProviderId` (Task 1)
- Produces: `AgentResponse` 필드 `boolean available`, `String unavailableReason`; `AgentService.assignProvider(Long agentId, Long providerId)`; `PUT /agents/{id}/provider` body `{ providerId }`

- [ ] **Step 1: 실패하는 테스트 작성**

`T/agent/AgentServiceTest.java`:

```java
package com.agent.dock.agent;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.provider.ProviderKey;
import com.agent.dock.role.AgentRole;
import com.agent.dock.role.AgentRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    @Mock AgentRepository agentRepository;
    @Mock AgentRoleRepository roleRepository;
    @Mock PermissionProfileRepository permissionProfileRepository;
    @Mock AiProviderRepository providerRepository;
    @Mock AiConnectionRepository connectionRepository;
    @InjectMocks AgentService service;

    private AiProvider provider(long id, boolean deleted) {
        AiProvider provider = new AiProvider();
        provider.setId(id);
        provider.setKey(ProviderKey.CLAUDE_CODE);
        provider.setName("Claude Code");
        provider.setDeletedAt(deleted ? Instant.now() : null);
        return provider;
    }

    private Agent agentWith(AiProvider provider) {
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setName("Dev A");
        agent.setRole(new AgentRole());
        agent.setPermissionProfile(new PermissionProfile());
        agent.setProvider(provider);
        return agent;
    }

    @Test
    void findAllMarksAgentOfDeletedProviderUnavailable() {
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, true))));

        var responses = service.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).available()).isFalse();
        assertThat(responses.get(0).unavailableReason()).isEqualTo("PROVIDER_DELETED");
    }

    @Test
    void findAllMarksAgentWithConnectedConnectionAvailable() {
        AiConnection connection = new AiConnection();
        connection.setProviderId(7L);
        connection.setStatus(ConnectionStatus.CONNECTED);
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, false))));
        when(connectionRepository.findByProviderIdIn(any())).thenReturn(List.of(connection));

        var responses = service.findAll();

        assertThat(responses.get(0).available()).isTrue();
        assertThat(responses.get(0).unavailableReason()).isNull();
    }

    @Test
    void assignProviderToDeletedOrMissingProviderIsNotFoundAndChangesNothing() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentWith(provider(7L, true))));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignProvider(1L, 9L)).isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void assignProviderSwitchesProviderAndClearsConnection() {
        Agent agent = agentWith(provider(7L, true));
        agent.setConnection(new AiConnection());
        AiProvider replacement = provider(9L, false);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(replacement));
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agent));

        service.assignProvider(1L, 9L);

        verify(agentRepository).save(agent);
        assertThat(agent.getProvider()).isSameAs(replacement);
        assertThat(agent.getConnection()).isNull();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.agent.AgentServiceTest"`
Expected: 컴파일 오류(`available()`, `unavailableReason()`, `assignProvider` 없음).

- [ ] **Step 3: DTO 와 응답 수정**

`B/agent/AssignProviderRequest.java`:

```java
package com.agent.dock.agent;

import jakarta.validation.constraints.NotNull;

public record AssignProviderRequest(@NotNull Long providerId) {
}
```

`B/agent/AgentResponse.java` 전체를 교체한다.

```java
package com.agent.dock.agent;

import com.agent.dock.permission.PermissionProfileResponse;
import com.agent.dock.provider.AiConnectionResponse;
import com.agent.dock.provider.AiProviderSummary;
import com.agent.dock.role.AgentRoleResponse;

import java.time.Instant;
import java.util.Map;

public record AgentResponse(
        Long id, String name,
        Long roleId, AgentRoleResponse role,
        Long permissionProfileId, PermissionProfileResponse permissionProfile,
        Long providerId, AiProviderSummary provider,
        Long connectionId, AiConnectionResponse connection,
        String model, String mode, Map<String, Object> profile,
        boolean available, String unavailableReason,
        Instant createdAt, Instant updatedAt
) {
    public static AgentResponse from(Agent a, AgentAvailability.Result availability) {
        return new AgentResponse(
                a.getId(), a.getName(),
                a.getRoleId(), AgentRoleResponse.from(a.getRole()),
                a.getPermissionProfileId(), PermissionProfileResponse.from(a.getPermissionProfile()),
                a.getProviderId(), AiProviderSummary.from(a.getProvider()),
                a.getConnectionId(), a.getConnection() == null ? null : AiConnectionResponse.from(a.getConnection()),
                a.getModel(), a.getMode(), a.getProfile(),
                availability.available(), availability.reason() == null ? null : availability.reason().name(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 4: AgentService 수정**

`B/agent/AgentService.java` 전체를 교체한다. (`assignProvider` 는 의도적으로 `@Transactional` 이 아니다. 같은 영속성 컨텍스트에서 다시 읽으면 shadow FK 필드 `providerId` 가 옛 값으로 남기 때문에, 저장을 커밋한 뒤 `findOne` 으로 새로 조회한다.)

```java
package com.agent.dock.agent;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.role.AgentRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentRoleRepository roleRepository;
    private final PermissionProfileRepository permissionProfileRepository;
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;

    public List<AgentResponse> findAll() {
        List<Agent> agents = agentRepository.findAllWithRelations();
        Set<Long> providerIds = agents.stream().map(a -> a.getProvider().getId()).collect(Collectors.toSet());
        Map<Long, List<AiConnection>> connectionsByProvider = providerIds.isEmpty() ? Map.of()
                : connectionRepository.findByProviderIdIn(providerIds).stream()
                        .collect(Collectors.groupingBy(AiConnection::getProviderId));
        return agents.stream()
                .map(a -> toResponse(a, connectionsByProvider.getOrDefault(a.getProvider().getId(), List.of())))
                .toList();
    }

    public AgentResponse findOne(Long id) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        return toResponse(agent, connectionRepository.findByProviderId(agent.getProvider().getId()));
    }

    public AgentResponse create(CreateAgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.name());
        agent.setRole(roleRepository.findById(request.roleId())
                .orElseThrow(() -> new NotFoundException("AgentRole %d not found".formatted(request.roleId()))));
        agent.setPermissionProfile(permissionProfileRepository.findById(request.permissionProfileId())
                .orElseThrow(() -> new NotFoundException("PermissionProfile %d not found".formatted(request.permissionProfileId()))));
        agent.setProvider(providerRepository.findByIdAndDeletedAtIsNull(request.providerId())
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(request.providerId()))));
        if (request.connectionId() != null) {
            agent.setConnection(connectionRepository.findById(request.connectionId())
                    .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(request.connectionId()))));
        }
        agent.setModel(request.model());
        agent.setMode(request.mode());
        agent.setProfile(request.profile());
        Agent saved = agentRepository.save(agent);
        return findOne(saved.getId());
    }

    /** 사용 불가가 된 Agent 에 다른 Provider 를 다시 할당한다. 삭제되지 않은 Provider 만 가능하다. */
    public AgentResponse assignProvider(Long agentId, Long providerId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
        AiProvider provider = providerRepository.findByIdAndDeletedAtIsNull(providerId)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(providerId)));
        agent.setProvider(provider);
        agent.setConnection(null);
        agentRepository.save(agent);
        return findOne(agentId);
    }

    private AgentResponse toResponse(Agent agent, List<AiConnection> providerConnections) {
        return AgentResponse.from(agent, AgentAvailability.evaluate(agent.getProvider(), providerConnections));
    }
}
```

- [ ] **Step 5: 컨트롤러 엔드포인트 추가**

`B/agent/AgentController.java` 에 추가:

```java
    @PutMapping("/{id}/provider")
    public AgentResponse assignProvider(@PathVariable Long id, @Valid @RequestBody AssignProviderRequest request) {
        return service.assignProvider(id, request.providerId());
    }
```

- [ ] **Step 6: 통과 확인 + 전체 컴파일**

Run: `.\gradlew.bat test --tests "com.agent.dock.agent.AgentServiceTest" --tests "com.agent.dock.agent.AgentAvailabilityTest" --tests "com.agent.dock.provider.*" --tests "com.agent.dock.execution.ExecutionServiceGuardTest"`
Expected: PASS. (`AgentResponse.from(Agent)` 를 부르던 곳이 있으면 컴파일 오류가 난다. 조사 결과 `AgentService` 만 호출했으므로 나오지 않아야 한다.)

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src
git commit -m "feat: Agent 사용 가능 여부 응답과 Provider 재할당 API 추가"
```

---

### Task 5: 웹 로그인 세션 (백엔드)

**Files (모두 `B/provider/login/`, 패키지 `com.agent.dock.provider.login`):**
- Create: `LoginEvent.java`, `LoginProcess.java`, `PipeLoginProcess.java`, `LoginProcessFactory.java`, `AiLoginCommand.java`, `ClaudeCodeLoginCommand.java`, `LoginCommandRegistry.java`, `LoginSession.java`, `LoginSessionService.java`, `LoginController.java`, `LoginInputRequest.java`, `LoginSessionResponse.java`
- Test: `T/provider/login/LoginSessionServiceTest.java`

**Interfaces:**
- Consumes: `AiConnectionRepository.findWithProvider(Long)` (기존), `ProviderKey`
- Produces: `LoginSessionService.start(Long connectionId): String`, `subscribe(String sessionId, Consumer<LoginEvent>): Runnable`, `input(String sessionId, String text)`, `stop(String sessionId)`, `sweepIdle()`; API `POST /ai-connections/{id}/login`(201 `{sessionId}`), `GET /ai-connections/login-sessions/{sessionId}/stream`(SSE 이벤트 JSON `{type:'output'|'exit', content, exitCode}`), `POST .../input`(204), `DELETE .../login-sessions/{sessionId}`(204)

- [ ] **Step 1: 실패하는 테스트 작성**

`T/provider/login/LoginSessionServiceTest.java`:

```java
package com.agent.dock.provider.login;

import com.agent.dock.common.BadRequestException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ProviderKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    static class FakeLoginProcess implements LoginProcess {
        Consumer<String> outputListener = s -> { };
        IntConsumer exitListener = c -> { };
        List<String> started;
        final List<String> written = new ArrayList<>();
        boolean closed;
        boolean failStart;

        @Override public void start(List<String> command) throws IOException {
            if (failStart) throw new IOException("no such file");
            started = command;
        }
        @Override public void onOutput(Consumer<String> listener) { outputListener = listener; }
        @Override public void onExit(IntConsumer listener) { exitListener = listener; }
        @Override public void write(String text) { written.add(text); }
        @Override public void close() { closed = true; exitListener.accept(143); }

        void emit(String text) { outputListener.accept(text); }
        void exit(int code) { exitListener.accept(code); }
    }

    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Mock AiConnectionRepository connectionRepository;
    @Mock LoginProcessFactory processFactory;

    FakeLoginProcess process;
    TestClock clock;
    LoginSessionService service;

    private AiConnection connectionOf(ProviderKey key) {
        AiProvider provider = new AiProvider();
        provider.setKey(key);
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        return connection;
    }

    @BeforeEach
    void setUp() {
        process = new FakeLoginProcess();
        clock = new TestClock();
        service = new LoginSessionService(connectionRepository,
                new LoginCommandRegistry(List.of(new ClaudeCodeLoginCommand())), processFactory, clock);
    }

    private void givenClaudeConnection() {
        when(connectionRepository.findWithProvider(7L)).thenReturn(Optional.of(connectionOf(ProviderKey.CLAUDE_CODE)));
        when(processFactory.create()).thenReturn(process);
    }

    @Test
    void startRunsFixedClaudeLoginCommand() {
        givenClaudeConnection();

        String sessionId = service.start(7L);

        assertThat(sessionId).isNotBlank();
        assertThat(process.started.subList(1, 3)).containsExactly("auth", "login");
    }

    @Test
    void lateSubscriberReplaysEarlierOutputThenReceivesLiveOutput() {
        givenClaudeConnection();
        String sessionId = service.start(7L);
        process.emit("Open https://example.com/login");

        List<LoginEvent> got = new ArrayList<>();
        service.subscribe(sessionId, got::add);
        process.emit(" then paste code");

        assertThat(got).extracting(LoginEvent::content)
                .containsExactly("Open https://example.com/login", " then paste code");
    }

    @Test
    void exitEventIsDeliveredWithExitCode() {
        givenClaudeConnection();
        String sessionId = service.start(7L);
        List<LoginEvent> got = new ArrayList<>();
        service.subscribe(sessionId, got::add);

        process.exit(0);

        assertThat(got).hasSize(1);
        assertThat(got.get(0).exitEvent()).isTrue();
        assertThat(got.get(0).exitCode()).isEqualTo(0);
    }

    @Test
    void secondStartWhileActiveReturnsSameSession() {
        givenClaudeConnection();

        String first = service.start(7L);
        String second = service.start(7L);

        assertThat(second).isEqualTo(first);
        verify(processFactory, times(1)).create();
    }

    @Test
    void inputIsWrittenWithNewline() {
        givenClaudeConnection();
        String sessionId = service.start(7L);

        service.input(sessionId, "abc123");

        assertThat(process.written).containsExactly("abc123\n");
    }

    @Test
    void inputAfterExitIsRejected() {
        givenClaudeConnection();
        String sessionId = service.start(7L);
        process.exit(0);

        assertThatThrownBy(() -> service.input(sessionId, "late")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void providerWithoutLoginCommandIsRejected() {
        when(connectionRepository.findWithProvider(8L)).thenReturn(Optional.of(connectionOf(ProviderKey.CODEX)));

        assertThatThrownBy(() -> service.start(8L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CODEX");
    }

    @Test
    void missingCliBinaryEndsSessionWithMessageAndExitMinusOne() {
        process.failStart = true;
        givenClaudeConnection();
        String sessionId = service.start(7L);

        List<LoginEvent> got = new ArrayList<>();
        service.subscribe(sessionId, got::add);

        assertThat(got).hasSize(2);
        assertThat(got.get(0).content()).contains("CLI");
        assertThat(got.get(1).exitEvent()).isTrue();
        assertThat(got.get(1).exitCode()).isEqualTo(-1);
    }

    @Test
    void idleSessionIsKilledAfterTimeout() {
        givenClaudeConnection();
        service.start(7L);

        clock.advance(Duration.ofMinutes(6));
        service.sweepIdle();

        assertThat(process.closed).isTrue();
    }

    @Test
    void activityResetsIdleTimer() {
        givenClaudeConnection();
        service.start(7L);

        clock.advance(Duration.ofMinutes(4));
        process.emit("still working");
        clock.advance(Duration.ofMinutes(2));
        service.sweepIdle();

        assertThat(process.closed).isFalse();
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThatThrownBy(() -> service.subscribe("nope", e -> { })).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.input("nope", "x")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.stop("nope")).isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.provider.login.LoginSessionServiceTest"`
Expected: 컴파일 오류(패키지 `provider.login` 없음).

- [ ] **Step 3: 이벤트/프로세스 인터페이스 구현**

`B/provider/login/LoginEvent.java`:

```java
package com.agent.dock.provider.login;

/** 로그인 세션이 SSE 로 내보내는 이벤트. type 은 "output" 또는 "exit". */
public record LoginEvent(String type, String content, Integer exitCode) {

    public static LoginEvent output(String content) {
        return new LoginEvent("output", content, null);
    }

    public static LoginEvent exit(int exitCode) {
        return new LoginEvent("exit", null, exitCode);
    }

    /** JSON 직렬화 대상이 되지 않도록 bean getter 형식(isX)을 피한 이름을 쓴다. */
    public boolean exitEvent() {
        return "exit".equals(type);
    }
}
```

`B/provider/login/LoginProcess.java`:

```java
package com.agent.dock.provider.login;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 로그인 CLI 프로세스와의 입출력 경계. 지금은 파이프 구현(PipeLoginProcess)만 있고,
 * 메뉴형 CLI 가 필요해지면 PTY 구현으로 교체한다. 리스너는 start 전에 등록한다.
 */
public interface LoginProcess {
    void start(List<String> command) throws IOException;

    void onOutput(Consumer<String> listener);

    void onExit(IntConsumer listener);

    void write(String text) throws IOException;

    void close();
}
```

`B/provider/login/PipeLoginProcess.java`:

```java
package com.agent.dock.provider.login;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** ProcessBuilder(shell 미경유) 기반 구현. stderr 를 stdout 에 합쳐 읽는다. */
public class PipeLoginProcess implements LoginProcess {
    private Consumer<String> outputListener = text -> { };
    private IntConsumer exitListener = code -> { };
    private Process process;

    @Override
    public void start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        process = builder.start();

        Thread reader = new Thread(this::pump, "login-output");
        reader.setDaemon(true);
        reader.start();

        // 로그인 CLI 가 띄운 브라우저 등 자식 프로세스가 파이프를 잡고 있으면 EOF 가 오지 않을 수 있으므로,
        // 종료 감지는 프로세스 종료 이벤트로 하고 남은 출력은 잠깐만 기다린다.
        process.onExit().thenAccept(finished -> {
            try {
                reader.join(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exitListener.accept(finished.exitValue());
        });
    }

    private void pump() {
        try (Reader in = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                outputListener.accept(new String(buffer, 0, read));
            }
        } catch (IOException ignored) {
            // 프로세스를 종료하면 스트림이 닫힌다
        }
    }

    @Override
    public void onOutput(Consumer<String> listener) {
        this.outputListener = listener;
    }

    @Override
    public void onExit(IntConsumer listener) {
        this.exitListener = listener;
    }

    @Override
    public void write(String text) throws IOException {
        OutputStream out = process.getOutputStream();
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroyForcibly();
        }
    }
}
```

`B/provider/login/LoginProcessFactory.java`:

```java
package com.agent.dock.provider.login;

import org.springframework.stereotype.Component;

/** 세션마다 새 LoginProcess 를 만든다. 테스트에서는 가짜 프로세스로 대체한다. */
@Component
public class LoginProcessFactory {
    public LoginProcess create() {
        return new PipeLoginProcess();
    }
}
```

- [ ] **Step 4: 로그인 명령 구현**

`B/provider/login/AiLoginCommand.java`:

```java
package com.agent.dock.provider.login;

import com.agent.dock.provider.ProviderKey;

import java.util.List;

/**
 * Provider 별 로그인 명령. 서버 고정값이며 사용자 입력으로 만들지 않는다.
 * 새 Provider 를 지원할 때는 이 인터페이스를 구현하고 @Component 로 등록하기만 하면 된다.
 */
public interface AiLoginCommand {
    ProviderKey providerKey();

    List<String> command();
}
```

`B/provider/login/ClaudeCodeLoginCommand.java`:

```java
package com.agent.dock.provider.login;

import com.agent.dock.provider.ProviderKey;
import org.springframework.stereotype.Component;

import java.util.List;

/** `claude auth login`. 바이너리는 CLAUDE_CODE_BIN 으로 override 한다(기본 claude). */
@Component
public class ClaudeCodeLoginCommand implements AiLoginCommand {

    @Override
    public ProviderKey providerKey() {
        return ProviderKey.CLAUDE_CODE;
    }

    @Override
    public List<String> command() {
        String binary = System.getenv().getOrDefault("CLAUDE_CODE_BIN", "claude");
        return List.of(binary, "auth", "login");
    }
}
```

`B/provider/login/LoginCommandRegistry.java`:

```java
package com.agent.dock.provider.login;

import com.agent.dock.provider.ProviderKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** provider key → 로그인 명령 매핑. ProbeRegistry 와 같은 방식. */
@Component
public class LoginCommandRegistry {
    private final Map<ProviderKey, AiLoginCommand> commands;

    public LoginCommandRegistry(List<AiLoginCommand> commandBeans) {
        this.commands = commandBeans.stream()
                .collect(Collectors.toMap(AiLoginCommand::providerKey, command -> command));
    }

    public Optional<AiLoginCommand> find(ProviderKey providerKey) {
        return Optional.ofNullable(commands.get(providerKey));
    }
}
```

- [ ] **Step 5: 세션 구현**

`B/provider/login/LoginSession.java`:

```java
package com.agent.dock.provider.login;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 로그인 프로세스 한 번의 수명. 이벤트를 모두 쌓아 두고, 구독자에게 지금까지의 이벤트를 재생한 뒤 실시간으로 전달한다
 * (구독이 프로세스 시작보다 늦어도 인증 URL 출력을 놓치지 않기 위함).
 */
class LoginSession {
    private final String id;
    private final Long connectionId;
    private final LoginProcess process;
    private final Clock clock;
    private final List<LoginEvent> events = new ArrayList<>();
    private final List<Consumer<LoginEvent>> subscribers = new ArrayList<>();
    private boolean finished;
    private Instant lastActivity;

    LoginSession(String id, Long connectionId, LoginProcess process, Clock clock) {
        this.id = id;
        this.connectionId = connectionId;
        this.process = process;
        this.clock = clock;
        this.lastActivity = clock.instant();
    }

    String id() {
        return id;
    }

    Long connectionId() {
        return connectionId;
    }

    LoginProcess process() {
        return process;
    }

    synchronized boolean finished() {
        return finished;
    }

    synchronized Instant lastActivity() {
        return lastActivity;
    }

    synchronized void touch() {
        lastActivity = clock.instant();
    }

    synchronized void publish(LoginEvent event) {
        events.add(event);
        lastActivity = clock.instant();
        if (event.exitEvent()) {
            finished = true;
        }
        for (Consumer<LoginEvent> subscriber : List.copyOf(subscribers)) {
            subscriber.accept(event);
        }
    }

    synchronized Runnable subscribe(Consumer<LoginEvent> subscriber) {
        events.forEach(subscriber);
        if (!finished) {
            subscribers.add(subscriber);
        }
        return () -> {
            synchronized (LoginSession.this) {
                subscribers.remove(subscriber);
            }
        };
    }
}
```

`B/provider/login/LoginSessionService.java`:

```java
package com.agent.dock.provider.login;

import com.agent.dock.common.BadRequestException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 웹 로그인 패널의 백엔드. Provider CLI 의 로그인 명령을 실행하고 출력을 세션에 쌓아 SSE 로 전달한다.
 * 자격증명은 CLI 가 자기 세션에 저장하며 이 앱은 다루지 않는다. 오래 걸리는 작업이므로 @Transactional 을 쓰지 않는다.
 */
@Service
@Slf4j
public class LoginSessionService {
    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final AiConnectionRepository connectionRepository;
    private final LoginCommandRegistry commandRegistry;
    private final LoginProcessFactory processFactory;
    private final Clock clock;
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService sweeper;

    @Autowired
    public LoginSessionService(AiConnectionRepository connectionRepository, LoginCommandRegistry commandRegistry,
                               LoginProcessFactory processFactory) {
        this(connectionRepository, commandRegistry, processFactory, Clock.systemUTC());
    }

    LoginSessionService(AiConnectionRepository connectionRepository, LoginCommandRegistry commandRegistry,
                        LoginProcessFactory processFactory, Clock clock) {
        this.connectionRepository = connectionRepository;
        this.commandRegistry = commandRegistry;
        this.processFactory = processFactory;
        this.clock = clock;
    }

    @PostConstruct
    void startSweeper() {
        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "login-session-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::sweepIdle, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        sessions.values().forEach(session -> session.process().close());
    }

    /** 로그인 세션을 시작하고 sessionId 를 돌려준다. Connection 당 활성 세션은 하나이며 이미 있으면 그것을 돌려준다. */
    public String start(Long connectionId) {
        // provider 를 fetch join 으로 함께 읽는다(open-in-view=false 라 지연 로딩 불가)
        AiConnection connection = connectionRepository.findWithProvider(connectionId)
                .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(connectionId)));
        var providerKey = connection.getProvider().getKey();
        List<String> command = commandRegistry.find(providerKey)
                .orElseThrow(() -> new BadRequestException("이 Provider 는 로그인 창을 아직 지원하지 않습니다: " + providerKey))
                .command();

        for (LoginSession existing : sessions.values()) {
            if (existing.connectionId().equals(connectionId) && !existing.finished()) {
                return existing.id();
            }
        }

        LoginProcess process = processFactory.create();
        LoginSession session = new LoginSession(UUID.randomUUID().toString(), connectionId, process, clock);
        sessions.put(session.id(), session);
        process.onOutput(chunk -> session.publish(LoginEvent.output(chunk)));
        process.onExit(code -> session.publish(LoginEvent.exit(code)));
        try {
            process.start(command);
        } catch (IOException ex) {
            log.warn("login CLI start failed: {}", command.get(0), ex);
            session.publish(LoginEvent.output("CLI 실행 실패: " + command.get(0)
                    + " 를 찾을 수 없습니다 (CLAUDE_CODE_BIN 으로 경로 지정 가능)"));
            session.publish(LoginEvent.exit(-1));
        }
        return session.id();
    }

    public Runnable subscribe(String sessionId, Consumer<LoginEvent> subscriber) {
        return find(sessionId).subscribe(subscriber);
    }

    /** 프로세스 stdin 으로 한 줄을 전달한다(인증 코드 붙여넣기용). */
    public void input(String sessionId, String text) {
        LoginSession session = find(sessionId);
        if (session.finished()) {
            throw new BadRequestException("로그인 세션이 이미 종료되었습니다");
        }
        try {
            session.process().write(text + "\n");
            session.touch();
        } catch (IOException ex) {
            throw new BadRequestException("입력을 전달하지 못했습니다: " + ex.getMessage());
        }
    }

    public void stop(String sessionId) {
        find(sessionId).process().close();
    }

    /** 유휴 시간이 지난 세션을 정리한다. 실행 중이면 종료시키고, 끝난 세션은 목록에서 지운다. */
    void sweepIdle() {
        Instant threshold = clock.instant().minus(IDLE_TIMEOUT);
        for (LoginSession session : List.copyOf(sessions.values())) {
            if (session.lastActivity().isAfter(threshold)) {
                continue;
            }
            if (session.finished()) {
                sessions.remove(session.id());
            } else {
                session.publish(LoginEvent.output("\n[5분 동안 활동이 없어 로그인 세션을 종료합니다]\n"));
                session.process().close();
            }
        }
    }

    private LoginSession find(String sessionId) {
        LoginSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Login session %s not found".formatted(sessionId));
        }
        return session;
    }
}
```

- [ ] **Step 6: 컨트롤러와 DTO**

`B/provider/login/LoginInputRequest.java`:

```java
package com.agent.dock.provider.login;

import jakarta.validation.constraints.NotNull;

public record LoginInputRequest(@NotNull String text) {
}
```

`B/provider/login/LoginSessionResponse.java`:

```java
package com.agent.dock.provider.login;

public record LoginSessionResponse(String sessionId) {
}
```

`B/provider/login/LoginController.java`:

```java
package com.agent.dock.provider.login;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/ai-connections")
@RequiredArgsConstructor
public class LoginController {
    private final LoginSessionService service;

    @PostMapping("/{connectionId}/login")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginSessionResponse start(@PathVariable Long connectionId) {
        return new LoginSessionResponse(service.start(connectionId));
    }

    @GetMapping(value = "/login-sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        Runnable unsubscribe = service.subscribe(sessionId, event -> {
            try {
                emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
                if (event.exitEvent()) {
                    emitter.complete();
                }
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(error -> unsubscribe.run());
        return emitter;
    }

    @PostMapping("/login-sessions/{sessionId}/input")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void input(@PathVariable String sessionId, @Valid @RequestBody LoginInputRequest request) {
        service.input(sessionId, request.text());
    }

    @DeleteMapping("/login-sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable String sessionId) {
        service.stop(sessionId);
    }
}
```

- [ ] **Step 7: 통과 확인**

Run: `.\gradlew.bat test --tests "com.agent.dock.provider.login.LoginSessionServiceTest"`
Expected: PASS (11 tests). 실패하면 `subscribe` 재생 순서(`events.forEach` 후 `subscribers.add`)와 `sweepIdle` 의 시간 비교(`isAfter`)를 먼저 확인한다.

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src
git commit -m "feat: 웹 로그인 세션(claude auth login 출력 스트리밍) 백엔드 추가"
```

---

### Task 6: 프론트 — API 클라이언트와 카드형 화면 + Provider 추가 모달

**Files:**
- Modify: `F/lib/api.ts`
- Rewrite: `F/pages/Providers.tsx`
- Create: `F/pages/ProviderCard.tsx`, `F/pages/AddProviderModal.tsx`, `F/pages/AddProviderModal.module.css`
- Modify: `F/pages/Providers.module.css`

프론트에는 테스트 프레임워크가 없다. 검증은 `npm run build:frontend`(타입 검사 + 번들)와 브라우저 확인으로 한다.

**Interfaces:**
- Consumes: Task 3/4/5 의 API
- Produces: `api.deleteProvider`, `createProviderConnection`, `deleteConnection`, `startLogin`, `sendLoginInput`, `stopLogin`, `assignAgentProvider`; `Agent.providerId/available/unavailableReason`

- [ ] **Step 1: api.ts 수정**

`F/lib/api.ts` 의 `request` 에서 204(본문 없음)를 처리한다. `return res.json() as Promise<T>;` 바로 앞에 추가:

```ts
  if (res.status === 204) {
    return undefined as T;
  }
```

`Agent` 인터페이스에 필드를 추가한다(`mode: string | null;` 아래).

```ts
  providerId: number;
  available: boolean;
  unavailableReason: 'PROVIDER_DELETED' | 'CONNECTION_NOT_CONNECTED' | null;
```

`api` 객체의 `checkConnection` 줄 아래에 추가:

```ts
  deleteProvider: (id: number) => request<void>(`/ai-providers/${id}`, { method: 'DELETE' }),
  createProviderConnection: (providerId: number) =>
    request<AiConnection>(`/ai-providers/${providerId}/connection`, { method: 'POST' }),
  deleteConnection: (id: number) => request<void>(`/ai-connections/${id}`, { method: 'DELETE' }),
  startLogin: (connectionId: number) =>
    request<{ sessionId: string }>(`/ai-connections/${connectionId}/login`, { method: 'POST' }),
  sendLoginInput: (sessionId: string, text: string) =>
    request<void>(`/ai-connections/login-sessions/${sessionId}/input`, {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
  stopLogin: (sessionId: string) =>
    request<void>(`/ai-connections/login-sessions/${sessionId}`, { method: 'DELETE' }),
```

`listAgents` 줄 아래에 추가:

```ts
  assignAgentProvider: (agentId: number, providerId: number) =>
    request<Agent>(`/agents/${agentId}/provider`, { method: 'PUT', body: JSON.stringify({ providerId }) }),
```

- [ ] **Step 2: Providers.module.css 확장**

`F/pages/Providers.module.css` 끝에 추가:

```css
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hint {
  color: var(--color-muted);
  font-size: 13px;
}

.cardHeader {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}

.dangerButton {
  background: transparent;
  color: var(--color-error);
  border-color: var(--color-error);
}

.dangerButton:hover {
  background: var(--color-error-bg);
}

.statusRow {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
}

.loginPanel {
  margin-top: var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--spacing-md);
}

.output {
  background: #1e1e1e;
  color: #e6e6e6;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius);
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  margin: 0 0 var(--spacing-sm);
}

.output a {
  color: #8ab4f8;
}

.inputRow {
  display: flex;
  gap: var(--spacing-sm);
}

.inputRow input {
  flex: 1;
}
```

- [ ] **Step 3: AddProviderModal 작성**

`F/pages/AddProviderModal.module.css`:

```css
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: var(--color-bg);
  color: var(--color-text);
  border-radius: var(--radius);
  padding: var(--spacing-lg);
  width: 420px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.hint {
  margin: 0;
  color: var(--color-muted);
  font-size: 13px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
```

`F/pages/AddProviderModal.tsx`:

```tsx
import { FormEvent, useState } from 'react';
import { api } from '../lib/api';
import styles from './AddProviderModal.module.css';

const DEFAULT_NAMES: Record<string, string> = {
  CLAUDE_CODE: 'Claude Code',
  CODEX: 'Codex',
  COMMAND_CODE: 'Command Code',
  GEMINI: 'Gemini',
};

interface AddProviderModalProps {
  registeredKeys: string[];
  onClose: () => void;
  onCreated: () => void;
}

export default function AddProviderModal({ registeredKeys, onClose, onCreated }: AddProviderModalProps) {
  const availableKeys = Object.keys(DEFAULT_NAMES).filter((k) => !registeredKeys.includes(k));
  const [key, setKey] = useState(availableKeys[0] ?? '');
  const [name, setName] = useState(DEFAULT_NAMES[availableKeys[0]] ?? '');
  const [error, setError] = useState<string | null>(null);

  const onKeyChange = (next: string) => {
    setKey(next);
    setName(DEFAULT_NAMES[next] ?? '');
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createProvider({ key, name });
      onCreated();
    } catch (err) {
      setError(String(err));
    }
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <form className={styles.modal} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>Provider 추가</h2>
        {availableKeys.length === 0 ? (
          <p className={styles.hint}>모든 Provider가 이미 등록되어 있습니다. 삭제한 Provider는 다시 등록할 수 있습니다.</p>
        ) : (
          <>
            <p className={styles.hint}>Provider를 등록하면 연결(Connection)이 함께 만들어집니다.</p>
            <select value={key} onChange={(e) => onKeyChange(e.target.value)} required>
              {availableKeys.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
            <input placeholder="표시 이름" value={name} onChange={(e) => setName(e.target.value)} required />
          </>
        )}
        {error && <p className="errorText">{error}</p>}
        <div className={styles.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={availableKeys.length === 0}>
            추가
          </button>
        </div>
      </form>
    </div>
  );
}
```

- [ ] **Step 4: ProviderCard 작성**

`F/pages/ProviderCard.tsx` (로그인 패널은 Task 7 에서 붙인다. 지금은 `loginOpen` 상태와 버튼만 두고 패널 자리는 비워 둔다):

```tsx
import { useState } from 'react';
import { AiProvider, api } from '../lib/api';
import styles from './Providers.module.css';

interface ProviderCardProps {
  provider: AiProvider;
  onChanged: () => void;
  onError: (message: string | null) => void;
}

export default function ProviderCard({ provider, onChanged, onError }: ProviderCardProps) {
  const connection = provider.connections?.[0] ?? null;
  const [checking, setChecking] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);

  const run = async (action: () => Promise<unknown>) => {
    onError(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      onError(String(e));
    }
  };

  const onCheck = async () => {
    if (!connection) return;
    setChecking(true);
    await run(() => api.checkConnection(connection.id));
    setChecking(false);
  };

  const onDeleteConnection = () => {
    if (!connection) return;
    if (!window.confirm('연결을 삭제하면 이 Provider의 Agent는 연결이 복구되기 전까지 실행되지 않습니다. 삭제할까요?')) return;
    setLoginOpen(false);
    run(() => api.deleteConnection(connection.id));
  };

  const onDeleteProvider = () => {
    if (!window.confirm('이 Provider를 쓰는 Agent는 사용 불가가 되며 다른 Provider를 다시 할당해야 합니다. 삭제할까요?')) return;
    run(() => api.deleteProvider(provider.id));
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <h2 className={styles.cardTitle}>
          {provider.name} <span className={styles.checkedAt}>({provider.key})</span>
        </h2>
        <button className={styles.dangerButton} onClick={onDeleteProvider}>
          Provider 삭제
        </button>
      </div>

      {connection ? (
        <>
          <div className={styles.statusRow}>
            <span
              className={
                connection.status === 'CONNECTED' ? `${styles.badge} ${styles.badgeOk}` : `${styles.badge} ${styles.badgeError}`
              }
            >
              {connection.status}
            </span>
            <span className={styles.checkedAt}>
              Last checked: {connection.lastCheckedAt ? new Date(connection.lastCheckedAt).toLocaleString() : '-'}
            </span>
          </div>
          {connection.lastError && <p className={styles.connError}>{connection.lastError}</p>}
          <div className={styles.actions}>
            <button onClick={onCheck} disabled={checking}>
              {checking ? '확인 중...' : '연결'}
            </button>
            {connection.status === 'ERROR' && (
              <button onClick={() => setLoginOpen(true)} disabled={loginOpen}>
                로그인
              </button>
            )}
            <button className={styles.dangerButton} onClick={onDeleteConnection}>
              연결 삭제
            </button>
          </div>
        </>
      ) : (
        <>
          <p className={styles.checkedAt}>Connection이 없습니다. 이 Provider의 Agent는 실행되지 않습니다.</p>
          <div className={styles.actions}>
            <button onClick={() => run(() => api.createProviderConnection(provider.id))}>연결 추가</button>
          </div>
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 5: Providers 페이지 재작성**

`F/pages/Providers.tsx` 전체를 교체한다.

```tsx
import { useEffect, useState } from 'react';
import { AiProvider, api } from '../lib/api';
import AddProviderModal from './AddProviderModal';
import ProviderCard from './ProviderCard';
import styles from './Providers.module.css';

export default function Providers() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = () => api.listProviders().then(setProviders).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <div className={styles.header}>
        <h1>Agent 연결 설정</h1>
        <button onClick={() => setAdding(true)}>+ Provider 추가</button>
      </div>
      <p className={styles.hint}>
        CLI 로그인 세션을 그대로 사용합니다(자격증명은 이 앱에 저장하지 않습니다). "연결"은 해당 CLI를 한 번 실행해 실제로
        응답하는지 확인합니다. 연결이 실패하면 "로그인"으로 이 화면에서 바로 로그인할 수 있습니다.
      </p>

      {error && <p className="errorText">{error}</p>}

      {providers.length === 0 && <p className={styles.checkedAt}>등록된 Provider가 없습니다.</p>}
      {providers.map((provider) => (
        <ProviderCard key={provider.id} provider={provider} onChanged={load} onError={setError} />
      ))}

      {adding && (
        <AddProviderModal
          registeredKeys={providers.map((p) => p.key)}
          onClose={() => setAdding(false)}
          onCreated={() => {
            setAdding(false);
            load();
          }}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 6: 타입 검사**

Run (저장소 루트): `npm run build:frontend`
Expected: `tsc --noEmit` 통과 + `vite build` 성공. (`Agents.tsx` 는 아직 새 `Agent` 필드를 쓰지 않으므로 통과해야 한다.)

- [ ] **Step 7: Commit**

```bash
git add apps/frontend/src
git commit -m "feat: Agent 연결 설정을 카드형 화면과 Provider 추가 모달로 재구성"
```

---

### Task 7: 프론트 — 로그인 패널

**Files:**
- Create: `F/pages/LoginPanel.tsx`
- Modify: `F/pages/ProviderCard.tsx`

**Interfaces:**
- Consumes: `api.startLogin/sendLoginInput/stopLogin`, SSE 이벤트 JSON `{type, content, exitCode}` (Task 5)
- Produces: `<LoginPanel connectionId onClose onExit(exitCode) />`

- [ ] **Step 1: LoginPanel 작성**

`F/pages/LoginPanel.tsx`:

```tsx
import { FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import styles from './Providers.module.css';

interface LoginPanelProps {
  connectionId: number;
  onClose: () => void;
  onExit: (exitCode: number) => void;
}

interface LoginEventData {
  type: 'output' | 'exit';
  content: string | null;
  exitCode: number | null;
}

// eslint 없이도 읽기 쉽게: ANSI 이스케이프 제거 후 http(s) URL 만 링크로 만든다
const ANSI_PATTERN = /\u001b\[[0-9;?]*[ -/]*[@-~]/g;
const URL_PATTERN = /(https?:\/\/[^\s]+)/g;

function renderOutput(text: string): ReactNode[] {
  return text
    .replace(ANSI_PATTERN, '')
    .split(URL_PATTERN)
    .map((part, index) =>
      index % 2 === 1 ? (
        <a key={index} href={part} target="_blank" rel="noreferrer">
          {part}
        </a>
      ) : (
        part
      ),
    );
}

export default function LoginPanel({ connectionId, onClose, onExit }: LoginPanelProps) {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [output, setOutput] = useState('');
  const [exitCode, setExitCode] = useState<number | null>(null);
  const [input, setInput] = useState('');
  const [error, setError] = useState<string | null>(null);
  const onExitRef = useRef(onExit);
  onExitRef.current = onExit;

  useEffect(() => {
    let source: EventSource | null = null;
    let startedId: string | null = null;
    let cancelled = false;

    api
      .startLogin(connectionId)
      .then(({ sessionId: id }) => {
        // 취소된 실행(개발 모드 StrictMode 재실행 등)은 같은 세션을 공유하므로 여기서 세션을 끊지 않는다
        if (cancelled) return;
        startedId = id;
        setSessionId(id);
        source = new EventSource(`${api.base}/ai-connections/login-sessions/${id}/stream`);
        source.onmessage = (event) => {
          try {
            const data: LoginEventData = JSON.parse(event.data);
            if (data.type === 'output') {
              setOutput((prev) => prev + (data.content ?? ''));
            } else if (data.type === 'exit') {
              setExitCode(data.exitCode);
              source?.close();
              onExitRef.current(data.exitCode ?? -1);
            }
          } catch {
            // ignore malformed event
          }
        };
        source.onerror = () => source?.close();
      })
      .catch((e) => setError(String(e)));

    return () => {
      cancelled = true;
      source?.close();
      if (startedId) api.stopLogin(startedId).catch(() => {});
    };
  }, [connectionId]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sessionId) return;
    setError(null);
    try {
      await api.sendLoginInput(sessionId, input);
      setInput('');
    } catch (err) {
      setError(String(err));
    }
  };

  return (
    <div className={styles.loginPanel}>
      <pre className={styles.output}>{output ? renderOutput(output) : '로그인 명령을 시작하는 중...'}</pre>
      {exitCode !== null && (
        <p className={styles.checkedAt}>
          {exitCode === 0 ? '로그인 명령이 끝났습니다. 연결을 다시 확인했습니다.' : `로그인 명령이 종료되었습니다 (exit ${exitCode}).`}
        </p>
      )}
      {error && <p className="errorText">{error}</p>}
      <form className={styles.inputRow} onSubmit={onSubmit}>
        <input
          placeholder="인증 코드 등 입력이 필요하면 여기에 붙여 넣고 전송"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={!sessionId || exitCode !== null}
        />
        <button type="submit" disabled={!sessionId || exitCode !== null || input === ''}>
          전송
        </button>
        <button type="button" onClick={onClose}>
          닫기
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: ProviderCard 에 패널 연결**

`F/pages/ProviderCard.tsx`: import 추가

```tsx
import LoginPanel from './LoginPanel';
```

`onDeleteConnection` 위에 로그인 종료 처리를 추가한다(로그인 명령이 성공적으로 끝나면 연결을 자동으로 다시 확인).

```tsx
  const onLoginExit = async (exitCode: number) => {
    if (exitCode !== 0 || !connection) return;
    await run(() => api.checkConnection(connection.id));
  };
```

연결 정보 블록(`<div className={styles.actions}> ... </div>`) 바로 아래, 삼항의 첫 분기 안 마지막에 패널을 렌더링한다.

```tsx
          {loginOpen && (
            <LoginPanel connectionId={connection.id} onClose={() => setLoginOpen(false)} onExit={onLoginExit} />
          )}
```

- [ ] **Step 3: 타입 검사**

Run: `npm run build:frontend`
Expected: 통과.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/src
git commit -m "feat: Agent 연결 설정에 웹 로그인 패널 추가"
```

---

### Task 8: 프론트 — Agents 화면 (사용 불가 표시, Run 비활성, Provider 재할당)

**Files:**
- Create: `F/pages/AgentProviderAssign.tsx`, `F/pages/AgentProviderAssign.module.css`
- Modify: `F/pages/Agents.tsx`, `F/pages/Agents.module.css`

**Interfaces:**
- Consumes: `Agent.available/unavailableReason/providerId`, `api.assignAgentProvider` (Task 6)

- [ ] **Step 1: 재할당 컴포넌트 작성**

`F/pages/AgentProviderAssign.module.css`:

```css
.row {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
}
```

`F/pages/AgentProviderAssign.tsx`:

```tsx
import { useState } from 'react';
import { Agent, AiProvider, api } from '../lib/api';
import styles from './AgentProviderAssign.module.css';

interface AgentProviderAssignProps {
  agent: Agent;
  providers: AiProvider[];
  onAssigned: () => void;
  onError: (message: string | null) => void;
}

export default function AgentProviderAssign({ agent, providers, onAssigned, onError }: AgentProviderAssignProps) {
  const [providerId, setProviderId] = useState('');
  const candidates = providers.filter((p) => p.id !== agent.providerId);

  const onApply = async () => {
    onError(null);
    try {
      await api.assignAgentProvider(agent.id, Number(providerId));
      setProviderId('');
      onAssigned();
    } catch (e) {
      onError(String(e));
    }
  };

  return (
    <span className={styles.row}>
      <select value={providerId} onChange={(e) => setProviderId(e.target.value)}>
        <option value="">Provider 변경</option>
        {candidates.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name} ({p.key})
          </option>
        ))}
      </select>
      <button type="button" onClick={onApply} disabled={providerId === ''}>
        적용
      </button>
    </span>
  );
}
```

- [ ] **Step 2: Agents.module.css 에 배지 스타일 추가**

`F/pages/Agents.module.css` 끝에 추가:

```css
.unavailable {
  margin-left: var(--spacing-sm);
  font-size: 12px;
  padding: 2px 6px;
  border-radius: var(--radius);
  border: 1px solid var(--color-error);
  color: var(--color-error);
}
```

- [ ] **Step 3: Agents.tsx 수정**

`F/pages/Agents.tsx`:

import 추가:

```tsx
import AgentProviderAssign from './AgentProviderAssign';
```

파일 상단 컴포넌트 바깥(`export default` 위)에 라벨 매핑 추가:

```tsx
const UNAVAILABLE_LABELS: Record<string, string> = {
  PROVIDER_DELETED: 'Provider 삭제됨',
  CONNECTION_NOT_CONNECTED: '연결 안 됨',
};
```

테이블을 다음으로 교체한다(기존 `<table className="table" ...>` 전체).

```tsx
      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Role</th>
            <th>Provider</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.id}>
              <td>
                {a.name}
                {!a.available && a.unavailableReason && (
                  <span className={styles.unavailable}>{UNAVAILABLE_LABELS[a.unavailableReason] ?? '사용 불가'}</span>
                )}
              </td>
              <td>{a.role.name}</td>
              <td>
                {a.provider.name}{' '}
                <AgentProviderAssign agent={a} providers={providers} onAssigned={loadAll} onError={setError} />
              </td>
              <td>
                <button
                  onClick={() => setRunTarget(a)}
                  disabled={!a.available}
                  title={a.available ? undefined : '사용 불가 상태입니다. Agent 연결 설정에서 연결을 확인하세요.'}
                >
                  Run
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
```

- [ ] **Step 4: 타입 검사**

Run: `npm run build:frontend`
Expected: 통과.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/src
git commit -m "feat: Agents 화면에 사용 불가 표시와 Provider 재할당 추가"
```

---

### Task 9: 통합 검증 + 문서 갱신

**Files:**
- Modify: `agents/CONVENTIONS.md`, `agents/HANDOVER.md`
- 임시(커밋 금지): 스크래치 디렉터리의 가짜 CLI 스크립트

- [ ] **Step 1: 단위 테스트 전체(DB 무관) 실행**

Run: `.\gradlew.bat test --tests "com.agent.dock.agent.*" --tests "com.agent.dock.provider.*" --tests "com.agent.dock.execution.ExecutionServiceGuardTest"`
Expected: PASS.

- [ ] **Step 2: 가짜 CLI 로 로그인 경로를 검증할 스크립트 준비**

로그인 완료(`exit 0`) → 자동 재확인 → `CONNECTED` 경로를 실제 계정 없이 확인하기 위해 스크래치 디렉터리에 `fake-claude.cmd` 를 만든다(저장소 밖). `CLAUDE_CODE_BIN` 이 probe 와 로그인 명령 둘 다에 쓰이므로 `-p` 인자(probe)와 `auth login`(로그인)을 모두 처리한다.

```bat
@echo off
if "%1"=="auth" (
  echo Open https://example.com/login to sign in
  set /p CODE=Paste code here:
  echo received %CODE%
  exit /b 0
)
echo OK
exit /b 0
```

- [ ] **Step 3: 백엔드/프론트를 검증 포트로 기동**

```powershell
$env:CLAUDE_CODE_BIN='<스크래치 경로>\fake-claude.cmd'
# JAVA_HOME 설정(공통 준비) 후, 저장소 루트에서
npm run dev:backend:test          # 8081, 로그에서 "Started AgentDockApplication" 확인
$env:VITE_API_BASE='http://localhost:8081'; npm run dev:frontend:test   # 3031
```

- [ ] **Step 4: API 수준 확인 (PowerShell)**

```powershell
$b='http://localhost:8081'
Invoke-RestMethod "$b/ai-providers" | ConvertTo-Json -Depth 4     # 4종, 각 connections 1개
$conn = (Invoke-RestMethod "$b/ai-providers")[0].connections[0].id
Invoke-RestMethod -Method Post "$b/ai-connections/$conn/check"     # 가짜 CLI → status CONNECTED
$s = Invoke-RestMethod -Method Post "$b/ai-connections/$conn/login"; $s   # sessionId
Invoke-RestMethod -Method Post "$b/ai-connections/login-sessions/$($s.sessionId)/input" -ContentType application/json -Body '{"text":"abc"}'
```

Expected: 확인 후 `status: CONNECTED`; 로그인 세션이 `sessionId` 를 돌려주고 입력이 204 로 받아들여진다.

- [ ] **Step 5: 브라우저 확인 (http://localhost:3031/providers)**

- Provider 카드 4개, 각 카드에 상태 배지/"연결"/"연결 삭제"/"Provider 삭제", 우측 상단 "+ Provider 추가".
- "연결" → CONNECTED. 임시로 `CLAUDE_CODE_BIN` 을 잘못된 경로로 바꿔 재기동하면 ERROR + "로그인" 버튼이 나타나고, 눌렀을 때 "CLI 실행 실패" 안내가 패널에 나온다.
- 가짜 CLI 상태에서 ERROR 를 만들 수 있는 방법이 없으면(가짜는 항상 성공) 로그인 패널은 Provider 카드의 status 를 DB 에서 `ERROR` 로 바꿔서 띄운다:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -U postgres -d AGENT_DOCK -c "UPDATE ai_connection SET status='ERROR' WHERE provider_id=(SELECT id FROM ai_provider WHERE key='CLAUDE_CODE' AND deleted_at IS NULL)"
```

  "로그인" → 패널에 `Open https://example.com/login ...` 가 링크로 표시 → 입력창에 코드 전송 → `received ...` 출력 후 종료, 자동 재확인으로 CONNECTED.
- Provider 삭제 → 카드 사라짐. "+ Provider 추가" 의 key 드롭다운에 삭제한 key 가 다시 나타나고 재등록된다(Review Focus 1).
- Agents 화면: Agent 를 하나 만든 뒤(Claude Code 사용) 그 Provider 를 삭제하면 Agent 에 "Provider 삭제됨" 배지, Run 비활성. "Provider 변경" 으로 다른 Provider 를 골라 적용하면 배지가 "연결 안 됨"(새 Provider 의 연결이 아직 CONNECTED 가 아니면) 또는 사라진다.
- 연결이 CONNECTED 가 아닌 Provider 의 Agent 로 `POST /executions` 를 보내면 409.

- [ ] **Step 6: 검증 데이터 정리와 서버 종료**

검증 중 만든 Agent/Provider 는 삭제하거나, 시드 4종이 삭제 상태로 남았다면 DB 를 원래대로 되돌린다(`flyway_schema_history` 는 유지). 삭제 상태 복구 예:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -U postgres -d AGENT_DOCK -c "UPDATE ai_provider SET deleted_at=NULL WHERE deleted_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ai_provider p2 WHERE p2.key=ai_provider.key AND p2.deleted_at IS NULL)"
```

서버 종료(백엔드 8081, 프론트 3031) 및 `CLAUDE_CODE_BIN` 환경변수 제거:

```powershell
Get-NetTCPConnection -LocalPort 8081,3031 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
Remove-Item Env:CLAUDE_CODE_BIN, Env:VITE_API_BASE -ErrorAction SilentlyContinue
```

- [ ] **Step 7: CONVENTIONS.md 갱신**

`agents/CONVENTIONS.md`:

1. "Backend 모듈" 의 `provider/` 항목을 다음 취지로 교체한다: `provider/` — AiProvider(논리 삭제, `deleted_at`)·AiConnection(Provider 당 1개) 관리와 연결 상태 확인. probe(`AiConnectionProbe`/`ProbeRegistry`/`ClaudeCodeProbe`)와 로그인 세션(`provider/login`: `AiLoginCommand`/`LoginProcess`/`LoginSessionService`, SSE)을 포함한다. Credential 은 DB에 평문 저장 금지.
2. "DB 스키마" 에 다음 항목을 추가한다: `ai_provider.deleted_at` 은 논리 삭제 시각이며 `key` 유일성은 부분 유니크 인덱스(`ai_provider_key_active`, `WHERE deleted_at IS NULL`)로 삭제되지 않은 행에만 적용된다. V4 는 4종 Provider 와 Connection 을 시드한다.
3. "AI 연결과 자격증명" 섹션의 "CODEX/COMMAND_CODE/GEMINI 는 ..." 문장 뒤에 추가한다: Provider 는 4종이 시드되고 삭제는 논리 삭제다. Connection 은 Provider 당 1개다. 연결 확인이 실패하면 웹 화면의 로그인 패널이 `POST /ai-connections/{id}/login` 으로 CLI 로그인 명령(서버 고정값, 현재 CLAUDE_CODE 만)을 실행하고 출력을 SSE 로 보여 준다(`GET /ai-connections/login-sessions/{sessionId}/stream`). 로그인 명령이 없는 Provider 는 "미지원"으로 안내한다.
4. "Permission Enforcement" 섹션 끝에 추가한다: 실행 가드 — Provider 가 논리 삭제됐거나 Provider 의 Connection 이 `CONNECTED` 가 아니면 `ExecutionService` 가 Runtime 을 호출하지 않고 409 로 거부한다(`AgentAvailability`, 새 실행만 차단하며 실행 중인 Execution 은 그대로 둔다). 사용 불가 Agent 는 `PUT /agents/{id}/provider` 로 다른 Provider 를 재할당해 복구한다. Agent 응답에는 `available`, `unavailableReason` 이 있다.
5. "코딩 컨벤션" 의 shadow FK 항목 옆에 한 줄 추가: 저장 후 응답에서 FK 가 필요하지만 같은 트랜잭션에서 다시 읽을 수 없는 경우(`AgentService.assignProvider`)는 `@Transactional` 없이 저장을 커밋한 뒤 새로 조회한다.

- [ ] **Step 8: HANDOVER.md 갱신**

`agents/HANDOVER.md`:

- 제목 날짜를 오늘 기준으로 유지하고, "2. 브랜치와 현재 상태" 의 `feat/ai-connection` 설명에 이번 작업(Agent 연결 설정 재구성: Provider 시드·논리 삭제, 웹 로그인 패널, 연결 상태 종속 실행 가드, Agent Provider 재할당)을 추가한다.
- "3. 바로 다음에 할 일" 에서 "CONNECTED 경로 검증" 항목을 완료 처리하고(사용자가 `claude auth login` 후 연결 확인이 성공함), "로그인 안내 UX" 항목을 완료로 바꾼다. 남은 후속으로 "Codex/Gemini/Command Code 로그인 명령·probe·Runtime(CLI 설치 후)"와 "메뉴형 CLI 가 필요해지면 `LoginProcess` PTY 구현"을 적는다.
- "5. 반복해서 밟은 함정" 에 추가한다: (1) 삭제(204) 응답은 본문이 없어 프론트 `request()` 가 204 를 따로 처리한다. (2) 로그인 CLI 가 띄운 자식 프로세스가 파이프를 잡으면 stdout EOF 가 오지 않으므로 종료는 `Process.onExit()` 로 감지한다.

- [ ] **Step 9: 최종 빌드와 커밋**

```powershell
.\gradlew.bat compileJava compileTestJava   # apps/backend
npm run build:frontend                       # 저장소 루트
```

```bash
git add agents/CONVENTIONS.md agents/HANDOVER.md
git commit -m "docs: Agent 연결 설정 구조 변경을 CONVENTIONS/HANDOVER에 반영"
```

- [ ] **Step 10: 브랜치 마무리**

`superpowers:finishing-a-development-branch` 를 사용한다. PR 제목 예: `[Feat] Agent 연결 설정 재구성(Provider 시드·논리 삭제, 웹 로그인 패널, 연결 상태 종속 실행)`. PR 링크: `https://github.com/seop-kim/AgentDock/compare/dev...feat/ai-connection?expand=1` (`gh` 는 없다).
