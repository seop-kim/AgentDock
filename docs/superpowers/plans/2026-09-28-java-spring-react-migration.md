# Java/Spring Boot + React 스택 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `apps/backend`(NestJS+Prisma)를 Java 21 + Spring Boot 3(Gradle, JPA+QueryDSL, Flyway)로, `apps/frontend`(Next.js)를 Vite+React+React Router로 1:1 포팅한다. 기능 추가 없음, User/인증은 범위 밖.

**Architecture:** 기존 6개 백엔드 모듈(ai-provider, agent, role, permission, workspace, execution)과 런타임 계층(AgentRuntime/ClaudeCodeRuntime/RuntimeRegistry/ProcessService)을 Spring 패키지로 그대로 대응시킨다. Entity↔JSON 직렬화 순환 참조를 피하기 위해 모든 Controller는 Entity를 직접 반환하지 않고 Response record(DTO)로 매핑한다. Execution의 SSE는 `Map<String, List<SseEmitter>>` fan-out으로, DB PK(Long)와 Runtime/스트림 키(String)를 분리해 관리한다. 프론트는 페이지 컴포넌트/CSS Modules를 거의 그대로 옮기고 Next.js 전용 요소만 제거한다.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Gradle(Kotlin DSL), Spring Data JPA + QueryDSL 5.1.0, Flyway, PostgreSQL, Lombok · Vite 5 + React 18 + React Router 6, CSS Modules

**Spec:** [docs/superpowers/specs/2026-09-28-java-spring-react-migration-brief.md](../specs/2026-09-28-java-spring-react-migration-brief.md)

## Global Constraints

- Java 21, Spring Boot 3.3.4, Gradle Kotlin DSL — 브리프 3절
- DB 컬럼명은 snake_case로 새로 설계한다(기존 Prisma의 camelCase 컬럼명 방식은 유지하지 않음). API JSON 필드명(camelCase)은 브리프 6절과 정확히 동일해야 한다 — 이 변경은 DB 내부 구현 세부사항이며 프론트 계약에는 영향 없음
- 모든 REST 응답은 Entity를 직접 반환하지 않고 Response record로 매핑한다(양방향 관계 순환 참조로 인한 Jackson 무한 직렬화 방지)
- Permission Enforcement는 `/executions` POST 처리 중 Runtime 호출 **전에** 서버에서 실제로 차단한다(`PermissionAction.TERMINAL_EXECUTE`) — 생략 금지
- Workspace는 UNC/네트워크 경로(`\\...`, `//...`)만 차단하고 그 외 경로 제한은 두지 않는다
- `ProcessBuilder`는 shell을 경유하지 않고 직접 실행한다(명령 인젝션 방지)
- 기존 `apps/backend`, `apps/frontend`는 삭제 후 같은 경로에 새로 생성한다(병행 운영하지 않음)
- 검증용으로 서버를 직접 띄울 때는 사용자 개발 포트(8080/3030)가 아니라 테스트 포트(8081/3031)를 쓰고, 검증이 끝나면 즉시 종료한다 — `agents/CONVENTIONS.md`

## Review Focus

- UNC 경로(`\\server\share`)로 Workspace 생성/브라우징 시도 → 400, 실제 파일시스템 접근 전에 거부되어야 한다 (Task 8에서 검증)
- 정수가 아닌 경로 파라미터(`GET /agents/abc`) → 400, 500이나 뻗는 동작이 아니어야 한다 (Task 10에서 검증)
- `terminalExecute: false`인 PermissionProfile을 가진 Agent로 Execution 생성 → 403이고 프로세스가 실제로 spawn되지 않아야 한다 (Task 11에서 검증)
- Agent 응답의 `provider`/`connection` 중첩 객체 직렬화 → 양방향 관계 때문에 무한 재귀(StackOverflow)로 죽지 않고 정상 JSON이 나와야 한다 (Task 5, 10에서 검증)
- 존재하지 않는 경로 또는 파일(디렉터리 아님)로 Workspace 생성 시도 → 400, 서버가 그 경로에 대해 아무 것도 실행/읽기 시도하지 않아야 한다 (Task 8에서 검증)

---

## Task 1: 백엔드 스캐폴딩 — 기존 NestJS 삭제, Gradle Spring Boot 프로젝트 생성

**Files:**
- Delete: `apps/backend/` 전체(기존 NestJS 프로�트)
- Create: `apps/backend/build.gradle.kts`
- Create: `apps/backend/settings.gradle.kts`
- Create: `apps/backend/gradle.properties`
- Create: `apps/backend/src/main/java/com/agentdock/backend/BackendApplication.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/common/WebConfig.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/common/BadRequestException.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/common/NotFoundException.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/common/ForbiddenException.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/common/GlobalExceptionHandler.java`
- Create: `apps/backend/src/main/resources/application.yml`
- Create: `apps/backend/.env.example` (참고용 — Spring은 `.env`를 직접 읽지 않으므로 실제로는 OS 환경변수나 `--spring.config.*`로 주입한다는 설명 포함)

**Interfaces:**
- Produces: `BadRequestException`, `NotFoundException`, `ForbiddenException` (전부 `RuntimeException` 상속, 이후 모든 모듈이 이 셋으로 예외를 던진다)

- [ ] **Step 1: 기존 apps/backend 삭제**

```bash
git rm -r apps/backend
```

- [ ] **Step 2: Gradle 프로젝트 뼈대 생성**

`apps/backend/settings.gradle.kts`:
```kotlin
rootProject.name = "backend"
```

`apps/backend/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx1g
```

`apps/backend/build.gradle.kts`:
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.agentdock"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val querydslVersion = "5.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("com.querydsl:querydsl-jpa:$querydslVersion:jakarta")
    annotationProcessor("com.querydsl:querydsl-apt:$querydslVersion:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: BackendApplication 작성**

`apps/backend/src/main/java/com/agentdock/backend/BackendApplication.java`:
```java
package com.agentdock.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
```

- [ ] **Step 4: 공통 예외 클래스 + 글로벌 핸들러 작성**

`apps/backend/src/main/java/com/agentdock/backend/common/BadRequestException.java`:
```java
package com.agentdock.backend.common;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/common/NotFoundException.java`:
```java
package com.agentdock.backend.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/common/ForbiddenException.java`:
```java
package com.agentdock.backend.common;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/common/GlobalExceptionHandler.java`:
```java
package com.agentdock.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return body(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Invalid path parameter: " + ex.getName());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "statusCode", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/common/WebConfig.java`:
```java
package com.agentdock.backend.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

- [ ] **Step 5: application.yml 작성**

`apps/backend/src/main/resources/application.yml`:
```yaml
server:
  port: ${PORT:8080}

spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/AGENT_DOCK}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

`apps/backend/.env.example` (참고용 문서 — Spring Boot는 `.env` 파일을 직접 읽지 않는다. 로컬 실행 시 OS 환경변수로 주입하거나 `application-local.yml`을 만들어 쓴다):
```
# Spring Boot는 .env를 자동으로 읽지 않습니다. 아래 값을 OS 환경변수로 export 하거나
# apps/backend/src/main/resources/application-local.yml (gitignore 대상)을 만들어 override 하세요.
DATABASE_URL=jdbc:postgresql://localhost:5432/AGENT_DOCK
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=
PORT=8080
CLAUDE_CODE_BIN=claude
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava` (최초 실행이라 `gradle wrapper` 태스크가 없으면 로컬에 설치된 `gradle`로 `gradle wrapper --gradle-version 8.10`을 먼저 실행해 wrapper를 생성한다)
Expected: `BUILD SUCCESSFUL` (아직 DB 연결/Flyway는 시도하지 않는 단계이므로 컴파일만 통과하면 된다)

- [ ] **Step 7: 커밋**

```bash
git add apps/backend
git commit -m "feat(backend): Spring Boot 프로젝트 스캐폴딩(Gradle, 공통 예외/CORS)"
```

---

## Task 2: Flyway 마이그레이션 — 전체 스키마

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V1__init.sql`

**Interfaces:**
- Produces: 8개 테이블(`ai_provider`, `ai_connection`, `agent_role`, `permission_profile`, `workspace`, `agent`, `execution`, `execution_log`) — snake_case 컬럼명. Task 3의 JPA 엔티티가 이 스키마와 정확히 일치해야 한다(`ddl-auto: validate`)

- [ ] **Step 1: 마이그레이션 SQL 작성**

`apps/backend/src/main/resources/db/migration/V1__init.sql`:
```sql
CREATE TABLE ai_provider (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    capabilities JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_connection (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_provider(id),
    account_name VARCHAR(255),
    credential_reference VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE permission_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    file_read BOOLEAN NOT NULL DEFAULT false,
    file_write BOOLEAN NOT NULL DEFAULT false,
    terminal_execute BOOLEAN NOT NULL DEFAULT false,
    git_status BOOLEAN NOT NULL DEFAULT true,
    git_diff BOOLEAN NOT NULL DEFAULT false,
    git_commit BOOLEAN NOT NULL DEFAULT false,
    git_push BOOLEAN NOT NULL DEFAULT false,
    db_read BOOLEAN NOT NULL DEFAULT false,
    db_write BOOLEAN NOT NULL DEFAULT false,
    db_schema_change BOOLEAN NOT NULL DEFAULT false,
    deploy BOOLEAN NOT NULL DEFAULT false,
    external_network_access BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    path VARCHAR(1024) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES agent_role(id),
    permission_profile_id BIGINT NOT NULL REFERENCES permission_profile(id),
    provider_id BIGINT NOT NULL REFERENCES ai_provider(id),
    connection_id BIGINT REFERENCES ai_connection(id),
    workspace_id BIGINT REFERENCES workspace(id),
    model VARCHAR(255),
    mode VARCHAR(64),
    profile JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE execution (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    workspace_id BIGINT NOT NULL REFERENCES workspace(id),
    prompt TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    exit_code INTEGER,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE execution_log (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES execution(id),
    stream VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_execution_log_execution_id ON execution_log(execution_id);
```

- [ ] **Step 2: 로컬 DB 초기화 후 Flyway 단독 적용 확인**

기존 Prisma가 쓰던 `AGENT_DOCK` DB를 깨끗하게 비운다(이 전환은 실데이터가 없는 상태에서 시작하는 것이 전제):
```bash
psql -U postgres -c 'DROP DATABASE IF EXISTS "AGENT_DOCK";'
psql -U postgres -c 'CREATE DATABASE "AGENT_DOCK";'
```

`apps/backend`에서 Flyway만 임시로 검증(Spring Boot 애플리케이션 기동 전에 Gradle Flyway 플러그인 없이도, Task 6에서 애플리케이션을 처음 기동할 때 자동 적용되므로 여기서는 SQL 문법 자체만 `psql`로 직접 실행해 오류가 없는지 확인):
```bash
psql -U postgres -d AGENT_DOCK -f apps/backend/src/main/resources/db/migration/V1__init.sql
```
Expected: 에러 없이 8개 테이블 생성. 확인 후 다시 비운다(Task 6에서 Flyway가 처음부터 적용하는 것을 검증해야 하므로):
```bash
psql -U postgres -c 'DROP DATABASE "AGENT_DOCK";'
psql -U postgres -c 'CREATE DATABASE "AGENT_DOCK";'
```

- [ ] **Step 3: 커밋**

```bash
git add apps/backend/src/main/resources/db/migration/V1__init.sql
git commit -m "feat(backend): Flyway 초기 스키마 마이그레이션 추가"
```

---

## Task 3: JPA 엔티티 + Enum

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/ProviderKey.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/ConnectionStatus.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionStatus.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/LogStream.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiProvider.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiConnection.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/role/AgentRole.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/PermissionProfile.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/Workspace.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/Agent.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/Execution.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionLog.java`

**Interfaces:**
- Produces: 8개 `@Entity` 클래스. FK 관계는 `@ManyToOne(fetch = LAZY)` + 읽기 전용 shadow scalar 컬럼(`roleId`, `providerId` 등)을 함께 둔다 — 나중에 Response record가 지연 로딩 없이 FK 정수값을 바로 읽을 수 있게 하기 위함
- Consumes: 없음(첫 엔티티 레이어)

- [ ] **Step 1: Enum 4개 작성**

`apps/backend/src/main/java/com/agentdock/backend/provider/ProviderKey.java`:
```java
package com.agentdock.backend.provider;

public enum ProviderKey {
    CLAUDE_CODE, CODEX, COMMAND_CODE, GEMINI
}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/ConnectionStatus.java`:
```java
package com.agentdock.backend.provider;

public enum ConnectionStatus {
    CONNECTED, DISCONNECTED, ERROR
}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionStatus.java`:
```java
package com.agentdock.backend.execution;

public enum ExecutionStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED
}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/LogStream.java`:
```java
package com.agentdock.backend.execution;

public enum LogStream {
    STDOUT, STDERR, SYSTEM
}
```

- [ ] **Step 2: AiProvider, AiConnection 엔티티 작성**

`apps/backend/src/main/java/com/agentdock/backend/provider/AiProvider.java`:
```java
package com.agentdock.backend.provider;

import com.agentdock.backend.agent.Agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ai_provider")
@Getter
@Setter
public class AiProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ProviderKey key;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> capabilities = new HashMap<>();

    @OneToMany(mappedBy = "provider")
    private List<AiConnection> connections;

    @OneToMany(mappedBy = "provider")
    private List<Agent> agents;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/AiConnection.java`:
```java
package com.agentdock.backend.provider;

import com.agentdock.backend.agent.Agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "ai_connection")
@Getter
@Setter
public class AiConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(name = "provider_id", insertable = false, updatable = false)
    private Long providerId;

    private String accountName;
    private String credentialReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;

    @OneToMany(mappedBy = "connection")
    private List<Agent> agents;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

- [ ] **Step 3: AgentRole, PermissionProfile, Workspace 엔티티 작성**

`apps/backend/src/main/java/com/agentdock/backend/role/AgentRole.java`:
```java
package com.agentdock.backend.role;

import com.agentdock.backend.agent.Agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "agent_role")
@Getter
@Setter
public class AgentRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @OneToMany(mappedBy = "role")
    private List<Agent> agents;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

`apps/backend/src/main/java/com/agentdock/backend/permission/PermissionProfile.java`:
```java
package com.agentdock.backend.permission;

import com.agentdock.backend.agent.Agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "permission_profile")
@Getter
@Setter
public class PermissionProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean fileRead = false;
    @Column(nullable = false)
    private boolean fileWrite = false;
    @Column(nullable = false)
    private boolean terminalExecute = false;
    @Column(nullable = false)
    private boolean gitStatus = true;
    @Column(nullable = false)
    private boolean gitDiff = false;
    @Column(nullable = false)
    private boolean gitCommit = false;
    @Column(nullable = false)
    private boolean gitPush = false;
    @Column(nullable = false)
    private boolean dbRead = false;
    @Column(nullable = false)
    private boolean dbWrite = false;
    @Column(nullable = false)
    private boolean dbSchemaChange = false;
    @Column(nullable = false)
    private boolean deploy = false;
    @Column(nullable = false)
    private boolean externalNetworkAccess = false;

    @OneToMany(mappedBy = "permissionProfile")
    private List<Agent> agents;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

`apps/backend/src/main/java/com/agentdock/backend/workspace/Workspace.java`:
```java
package com.agentdock.backend.workspace;

import com.agentdock.backend.agent.Agent;
import com.agentdock.backend.execution.Execution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "workspace")
@Getter
@Setter
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String path;

    private String description;

    @OneToMany(mappedBy = "workspace")
    private List<Agent> agents;

    @OneToMany(mappedBy = "workspace")
    private List<Execution> executions;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

- [ ] **Step 4: Agent 엔티티 작성**

`apps/backend/src/main/java/com/agentdock/backend/agent/Agent.java`:
```java
package com.agentdock.backend.agent;

import com.agentdock.backend.execution.Execution;
import com.agentdock.backend.permission.PermissionProfile;
import com.agentdock.backend.provider.AiConnection;
import com.agentdock.backend.provider.AiProvider;
import com.agentdock.backend.role.AgentRole;
import com.agentdock.backend.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "agent")
@Getter
@Setter
public class Agent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private AgentRole role;

    @Column(name = "role_id", insertable = false, updatable = false)
    private Long roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_profile_id", nullable = false)
    private PermissionProfile permissionProfile;

    @Column(name = "permission_profile_id", insertable = false, updatable = false)
    private Long permissionProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(name = "provider_id", insertable = false, updatable = false)
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private AiConnection connection;

    @Column(name = "connection_id", insertable = false, updatable = false)
    private Long connectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(name = "workspace_id", insertable = false, updatable = false)
    private Long workspaceId;

    private String model;
    private String mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> profile;

    @OneToMany(mappedBy = "agent")
    private List<Execution> executions;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

- [ ] **Step 5: Execution, ExecutionLog 엔티티 작성**

`apps/backend/src/main/java/com/agentdock/backend/execution/Execution.java`:
```java
package com.agentdock.backend.execution;

import com.agentdock.backend.agent.Agent;
import com.agentdock.backend.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "execution")
@Getter
@Setter
public class Execution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "agent_id", insertable = false, updatable = false)
    private Long agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "workspace_id", insertable = false, updatable = false)
    private Long workspaceId;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    private Instant startedAt;
    private Instant finishedAt;
    private Integer exitCode;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @OneToMany(mappedBy = "execution")
    private List<ExecutionLog> logs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionLog.java`:
```java
package com.agentdock.backend.execution;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "execution_log")
@Getter
@Setter
public class ExecutionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "execution_id", insertable = false, updatable = false)
    private Long executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogStream stream;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend
git commit -m "feat(backend): JPA 엔티티 8개 + Enum 4개 추가"
```

---

## Task 4: Repository 계층 + QueryDSL 설정

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/common/QueryDslConfig.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiConnectionRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/role/AgentRoleRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/PermissionProfileRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/AgentRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/AgentRepositoryCustom.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/AgentRepositoryImpl.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionRepository.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionLogRepository.java`

**Interfaces:**
- Consumes: Task 3의 엔티티 8개
- Produces: `AgentRepository.findAllWithRelations(): List<Agent>`, `AgentRepository.findByIdWithRelations(Long): Optional<Agent>` — 이후 AgentService/ExecutionService가 사용

- [ ] **Step 1: QueryDSL JPAQueryFactory Bean 등록**

`apps/backend/src/main/java/com/agentdock/backend/common/QueryDslConfig.java`:
```java
package com.agentdock.backend.common;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryDslConfig {
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
```

- [ ] **Step 2: 단순 Repository 6개 작성**

`apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderRepository.java`:
```java
package com.agentdock.backend.provider;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    @EntityGraph(attributePaths = "connections")
    List<AiProvider> findAllByOrderByNameAsc();
}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/AiConnectionRepository.java`:
```java
package com.agentdock.backend.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConnectionRepository extends JpaRepository<AiConnection, Long> {
    List<AiConnection> findByProviderId(Long providerId);
}
```

`apps/backend/src/main/java/com/agentdock/backend/role/AgentRoleRepository.java`:
```java
package com.agentdock.backend.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRoleRepository extends JpaRepository<AgentRole, Long> {
    List<AgentRole> findAllByOrderByNameAsc();
}
```

`apps/backend/src/main/java/com/agentdock/backend/permission/PermissionProfileRepository.java`:
```java
package com.agentdock.backend.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionProfileRepository extends JpaRepository<PermissionProfile, Long> {
    List<PermissionProfile> findAllByOrderByNameAsc();
}
```

`apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceRepository.java`:
```java
package com.agentdock.backend.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByOrderByNameAsc();
}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionRepository.java`:
```java
package com.agentdock.backend.execution;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionLogRepository.java`:
```java
package com.agentdock.backend.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);
}
```

- [ ] **Step 3: Agent QueryDSL 커스텀 Repository 작성**

`apps/backend/src/main/java/com/agentdock/backend/agent/AgentRepositoryCustom.java`:
```java
package com.agentdock.backend.agent;

import java.util.List;
import java.util.Optional;

public interface AgentRepositoryCustom {
    List<Agent> findAllWithRelations();
    Optional<Agent> findByIdWithRelations(Long id);
}
```

`apps/backend/src/main/java/com/agentdock/backend/agent/AgentRepositoryImpl.java`:
```java
package com.agentdock.backend.agent;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class AgentRepositoryImpl implements AgentRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<Agent> findAllWithRelations() {
        QAgent agent = QAgent.agent;
        return queryFactory.selectFrom(agent)
                .leftJoin(agent.role).fetchJoin()
                .leftJoin(agent.permissionProfile).fetchJoin()
                .leftJoin(agent.provider).fetchJoin()
                .leftJoin(agent.connection).fetchJoin()
                .leftJoin(agent.workspace).fetchJoin()
                .orderBy(agent.name.asc())
                .fetch();
    }

    @Override
    public Optional<Agent> findByIdWithRelations(Long id) {
        QAgent agent = QAgent.agent;
        Agent result = queryFactory.selectFrom(agent)
                .leftJoin(agent.role).fetchJoin()
                .leftJoin(agent.permissionProfile).fetchJoin()
                .leftJoin(agent.provider).fetchJoin()
                .leftJoin(agent.connection).fetchJoin()
                .leftJoin(agent.workspace).fetchJoin()
                .where(agent.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/agent/AgentRepository.java`:
```java
package com.agentdock.backend.agent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long>, AgentRepositoryCustom {
}
```

`QAgent`는 QueryDSL annotation processor가 `Agent.java` 컴파일 시 `build/generated/sources/annotationProcessor/java/main/com/agentdock/backend/agent/QAgent.java`에 자동 생성한다 — 직접 작성하지 않는다. Spring Data JPA는 `AgentRepositoryImpl`이라는 이름 규칙(`AgentRepository` + `Impl`)만으로 `AgentRepositoryCustom` 구현체를 자동 인식해 연결한다.

- [ ] **Step 4: 컴파일 확인 (QAgent 자동 생성 확인)**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. `apps/backend/build/generated/sources/annotationProcessor/java/main/com/agentdock/backend/agent/QAgent.java`가 생성되어 있어야 한다.

Run: `ls apps/backend/build/generated/sources/annotationProcessor/java/main/com/agentdock/backend/agent/`
Expected: `QAgent.java` 파일 존재

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend
git commit -m "feat(backend): Repository 계층 및 QueryDSL 설정 추가"
```

---

## Task 5: AiProvider 모듈 (DTO, Service, Controller)

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/CreateAiProviderRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/CreateAiConnectionRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiConnectionSummary.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderSummary.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiConnectionResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderController.java`

**Interfaces:**
- Consumes: `AiProviderRepository`, `AiConnectionRepository` (Task 4)
- Produces: `AiProviderSummary.from(AiProvider)`, `AiConnectionSummary.from(AiConnection)` — Task 10(Agent 모듈)이 Agent 응답에 이 두 record를 재사용한다

- [ ] **Step 1: 요청 DTO 작성**

`apps/backend/src/main/java/com/agentdock/backend/provider/CreateAiProviderRequest.java`:
```java
package com.agentdock.backend.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateAiProviderRequest(
        @NotNull ProviderKey key,
        @NotBlank String name,
        Map<String, Object> capabilities
) {}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/CreateAiConnectionRequest.java`:
```java
package com.agentdock.backend.provider;

import jakarta.validation.constraints.NotNull;

public record CreateAiConnectionRequest(
        @NotNull Long providerId,
        String accountName,
        String credentialReference
) {}
```

- [ ] **Step 2: 응답 DTO 작성 (순환 참조 방지 — Summary/Response 분리)**

`apps/backend/src/main/java/com/agentdock/backend/provider/AiConnectionSummary.java`:
```java
package com.agentdock.backend.provider;

import java.time.Instant;

public record AiConnectionSummary(Long id, String accountName, ConnectionStatus status, Instant createdAt, Instant updatedAt) {
    public static AiConnectionSummary from(AiConnection c) {
        return new AiConnectionSummary(c.getId(), c.getAccountName(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderSummary.java`:
```java
package com.agentdock.backend.provider;

import java.time.Instant;
import java.util.Map;

public record AiProviderSummary(Long id, ProviderKey key, String name, Map<String, Object> capabilities, Instant createdAt, Instant updatedAt) {
    public static AiProviderSummary from(AiProvider p) {
        return new AiProviderSummary(p.getId(), p.getKey(), p.getName(), p.getCapabilities(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/AiConnectionResponse.java`:
```java
package com.agentdock.backend.provider;

import java.time.Instant;

public record AiConnectionResponse(
        Long id, Long providerId, String accountName, String credentialReference,
        ConnectionStatus status, Instant createdAt, Instant updatedAt
) {
    public static AiConnectionResponse from(AiConnection c) {
        return new AiConnectionResponse(c.getId(), c.getProviderId(), c.getAccountName(),
                c.getCredentialReference(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderResponse.java`:
```java
package com.agentdock.backend.provider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiProviderResponse(
        Long id, ProviderKey key, String name, Map<String, Object> capabilities,
        List<AiConnectionSummary> connections, Instant createdAt, Instant updatedAt
) {
    public static AiProviderResponse from(AiProvider p) {
        List<AiConnectionSummary> connections = p.getConnections() == null ? List.of() :
                p.getConnections().stream().map(AiConnectionSummary::from).toList();
        return new AiProviderResponse(p.getId(), p.getKey(), p.getName(), p.getCapabilities(),
                connections, p.getCreatedAt(), p.getUpdatedAt());
    }
}
```

- [ ] **Step 3: Service 작성**

`apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderService.java`:
```java
package com.agentdock.backend.provider;

import com.agentdock.backend.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderService {
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;

    public List<AiProviderResponse> findAll() {
        return providerRepository.findAllByOrderByNameAsc().stream().map(AiProviderResponse::from).toList();
    }

    public AiProviderResponse create(CreateAiProviderRequest request) {
        AiProvider provider = new AiProvider();
        provider.setKey(request.key());
        provider.setName(request.name());
        provider.setCapabilities(request.capabilities() != null ? request.capabilities() : new HashMap<>());
        return AiProviderResponse.from(providerRepository.save(provider));
    }

    public AiConnectionResponse createConnection(CreateAiConnectionRequest request) {
        AiProvider provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(request.providerId())));
        AiConnection connection = new AiConnection();
        connection.setProvider(provider);
        connection.setAccountName(request.accountName());
        connection.setCredentialReference(request.credentialReference());
        return AiConnectionResponse.from(connectionRepository.save(connection));
    }

    public List<AiConnectionResponse> listConnections(Long providerId) {
        return connectionRepository.findByProviderId(providerId).stream().map(AiConnectionResponse::from).toList();
    }
}
```

- [ ] **Step 4: Controller 작성**

`apps/backend/src/main/java/com/agentdock/backend/provider/AiProviderController.java`:
```java
package com.agentdock.backend.provider;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai-providers")
@RequiredArgsConstructor
public class AiProviderController {
    private final AiProviderService service;

    @GetMapping
    public List<AiProviderResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    public AiProviderResponse create(@Valid @RequestBody CreateAiProviderRequest request) {
        return service.create(request);
    }

    @PostMapping("/connections")
    public AiConnectionResponse createConnection(@Valid @RequestBody CreateAiConnectionRequest request) {
        return service.createConnection(request);
    }

    @GetMapping("/{id}/connections")
    public List<AiConnectionResponse> listConnections(@PathVariable Long id) {
        return service.listConnections(id);
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/provider
git commit -m "feat(backend): AiProvider/AiConnection 모듈 포팅"
```

---

## Task 6: AgentRole 모듈

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/role/CreateAgentRoleRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/role/AgentRoleResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/role/RoleService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/role/RoleController.java`

**Interfaces:**
- Consumes: `AgentRoleRepository` (Task 4)
- Produces: `AgentRoleResponse.from(AgentRole)` — Task 10이 Agent 응답에 재사용

- [ ] **Step 1: DTO 작성**

`apps/backend/src/main/java/com/agentdock/backend/role/CreateAgentRoleRequest.java`:
```java
package com.agentdock.backend.role;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRoleRequest(@NotBlank String name, String description) {}
```

`apps/backend/src/main/java/com/agentdock/backend/role/AgentRoleResponse.java`:
```java
package com.agentdock.backend.role;

import java.time.Instant;

public record AgentRoleResponse(Long id, String name, String description, Instant createdAt, Instant updatedAt) {
    public static AgentRoleResponse from(AgentRole r) {
        return new AgentRoleResponse(r.getId(), r.getName(), r.getDescription(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
```

- [ ] **Step 2: Service, Controller 작성**

`apps/backend/src/main/java/com/agentdock/backend/role/RoleService.java`:
```java
package com.agentdock.backend.role;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final AgentRoleRepository repository;

    public List<AgentRoleResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(AgentRoleResponse::from).toList();
    }

    public AgentRoleResponse create(CreateAgentRoleRequest request) {
        AgentRole role = new AgentRole();
        role.setName(request.name());
        role.setDescription(request.description());
        return AgentRoleResponse.from(repository.save(role));
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/role/RoleController.java`:
```java
package com.agentdock.backend.role;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;

    @GetMapping
    public List<AgentRoleResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    public AgentRoleResponse create(@Valid @RequestBody CreateAgentRoleRequest request) {
        return service.create(request);
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/role
git commit -m "feat(backend): AgentRole 모듈 포팅"
```

---

## Task 7: PermissionProfile 모듈 (+ PermissionAction)

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/PermissionAction.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/CreatePermissionProfileRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/PermissionProfileResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/PermissionService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/permission/PermissionController.java`

**Interfaces:**
- Consumes: `PermissionProfileRepository` (Task 4)
- Produces: `PermissionProfileResponse.from(PermissionProfile)` (Task 10이 재사용), `PermissionService.isAllowed(PermissionProfile, PermissionAction): boolean` (Task 11의 Execution 생성이 반드시 호출해야 하는 Permission Enforcement 검사)

- [ ] **Step 1: PermissionAction enum, 요청/응답 DTO 작성**

`apps/backend/src/main/java/com/agentdock/backend/permission/PermissionAction.java`:
```java
package com.agentdock.backend.permission;

public enum PermissionAction {
    FILE_READ, FILE_WRITE, TERMINAL_EXECUTE, GIT_STATUS, GIT_DIFF,
    GIT_COMMIT, GIT_PUSH, DB_READ, DB_WRITE, DB_SCHEMA_CHANGE,
    DEPLOY, EXTERNAL_NETWORK_ACCESS
}
```

`apps/backend/src/main/java/com/agentdock/backend/permission/CreatePermissionProfileRequest.java`:
```java
package com.agentdock.backend.permission;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissionProfileRequest(
        @NotBlank String name,
        Boolean fileRead,
        Boolean fileWrite,
        Boolean terminalExecute,
        Boolean gitStatus,
        Boolean gitDiff,
        Boolean gitCommit,
        Boolean gitPush,
        Boolean dbRead,
        Boolean dbWrite,
        Boolean dbSchemaChange,
        Boolean deploy,
        Boolean externalNetworkAccess
) {}
```

`apps/backend/src/main/java/com/agentdock/backend/permission/PermissionProfileResponse.java`:
```java
package com.agentdock.backend.permission;

import java.time.Instant;

public record PermissionProfileResponse(
        Long id, String name, boolean fileRead, boolean fileWrite, boolean terminalExecute,
        boolean gitStatus, boolean gitDiff, boolean gitCommit, boolean gitPush,
        boolean dbRead, boolean dbWrite, boolean dbSchemaChange, boolean deploy,
        boolean externalNetworkAccess, Instant createdAt, Instant updatedAt
) {
    public static PermissionProfileResponse from(PermissionProfile p) {
        return new PermissionProfileResponse(p.getId(), p.getName(), p.isFileRead(), p.isFileWrite(),
                p.isTerminalExecute(), p.isGitStatus(), p.isGitDiff(), p.isGitCommit(), p.isGitPush(),
                p.isDbRead(), p.isDbWrite(), p.isDbSchemaChange(), p.isDeploy(), p.isExternalNetworkAccess(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
```

- [ ] **Step 2: Service 작성 (isAllowed 포함)**

`apps/backend/src/main/java/com/agentdock/backend/permission/PermissionService.java`:
```java
package com.agentdock.backend.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionProfileRepository repository;

    public List<PermissionProfileResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(PermissionProfileResponse::from).toList();
    }

    public PermissionProfileResponse create(CreatePermissionProfileRequest request) {
        PermissionProfile profile = new PermissionProfile();
        profile.setName(request.name());
        profile.setFileRead(Boolean.TRUE.equals(request.fileRead()));
        profile.setFileWrite(Boolean.TRUE.equals(request.fileWrite()));
        profile.setTerminalExecute(Boolean.TRUE.equals(request.terminalExecute()));
        profile.setGitStatus(request.gitStatus() == null || request.gitStatus());
        profile.setGitDiff(Boolean.TRUE.equals(request.gitDiff()));
        profile.setGitCommit(Boolean.TRUE.equals(request.gitCommit()));
        profile.setGitPush(Boolean.TRUE.equals(request.gitPush()));
        profile.setDbRead(Boolean.TRUE.equals(request.dbRead()));
        profile.setDbWrite(Boolean.TRUE.equals(request.dbWrite()));
        profile.setDbSchemaChange(Boolean.TRUE.equals(request.dbSchemaChange()));
        profile.setDeploy(Boolean.TRUE.equals(request.deploy()));
        profile.setExternalNetworkAccess(Boolean.TRUE.equals(request.externalNetworkAccess()));
        return PermissionProfileResponse.from(repository.save(profile));
    }

    /** Backend Tool Layer enforcement: Prompt 설명이 아니라 여기서 실제로 허용 여부를 결정한다. */
    public boolean isAllowed(PermissionProfile profile, PermissionAction action) {
        return switch (action) {
            case FILE_READ -> profile.isFileRead();
            case FILE_WRITE -> profile.isFileWrite();
            case TERMINAL_EXECUTE -> profile.isTerminalExecute();
            case GIT_STATUS -> profile.isGitStatus();
            case GIT_DIFF -> profile.isGitDiff();
            case GIT_COMMIT -> profile.isGitCommit();
            case GIT_PUSH -> profile.isGitPush();
            case DB_READ -> profile.isDbRead();
            case DB_WRITE -> profile.isDbWrite();
            case DB_SCHEMA_CHANGE -> profile.isDbSchemaChange();
            case DEPLOY -> profile.isDeploy();
            case EXTERNAL_NETWORK_ACCESS -> profile.isExternalNetworkAccess();
        };
    }
}
```

- [ ] **Step 3: Controller 작성**

`apps/backend/src/main/java/com/agentdock/backend/permission/PermissionController.java`:
```java
package com.agentdock.backend.permission;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permission-profiles")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService service;

    @GetMapping
    public List<PermissionProfileResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    public PermissionProfileResponse create(@Valid @RequestBody CreatePermissionProfileRequest request) {
        return service.create(request);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/permission
git commit -m "feat(backend): PermissionProfile 모듈 및 Enforcement 로직 포팅"
```

---

## Task 8: Workspace 모듈 (+ 폴더 브라우징, UNC 차단)

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/FsEntry.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceBrowseResult.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceFs.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/CreateWorkspaceRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceController.java`

**Interfaces:**
- Consumes: `WorkspaceRepository` (Task 4)
- Produces: `WorkspaceResponse.from(Workspace)` — Task 10이 Agent 응답에 재사용

- [ ] **Step 1: FsEntry, WorkspaceBrowseResult, WorkspaceFs(UNC 체크) 작성**

`apps/backend/src/main/java/com/agentdock/backend/workspace/FsEntry.java`:
```java
package com.agentdock.backend.workspace;

public record FsEntry(String name, String path) {}
```

`apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceBrowseResult.java`:
```java
package com.agentdock.backend.workspace;

import java.util.List;

public record WorkspaceBrowseResult(String path, String parentPath, List<FsEntry> entries) {}
```

`apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceFs.java`:
```java
package com.agentdock.backend.workspace;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 폴더 선택 UI의 최상위 시작 지점(Windows 드라이브 목록 / POSIX 루트)과
 * UNC/네트워크 경로 차단(NTLM 자격 증명 유출 방지)을 담당한다.
 */
@Component
public class WorkspaceFs {

    public List<FsEntry> listRoots() {
        List<FsEntry> roots = new ArrayList<>();
        for (File root : File.listRoots()) {
            String path = root.getAbsolutePath();
            roots.add(new FsEntry(path, path));
        }
        return roots;
    }

    public boolean isUncPath(String path) {
        return path.startsWith("\\\\") || path.startsWith("//");
    }
}
```

- [ ] **Step 2: 요청/응답 DTO 작성**

`apps/backend/src/main/java/com/agentdock/backend/workspace/CreateWorkspaceRequest.java`:
```java
package com.agentdock.backend.workspace;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(@NotBlank String name, @NotBlank String path, String description) {}
```

`apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceResponse.java`:
```java
package com.agentdock.backend.workspace;

import java.time.Instant;

public record WorkspaceResponse(Long id, String name, String path, String description, Instant createdAt, Instant updatedAt) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(w.getId(), w.getName(), w.getPath(), w.getDescription(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
```

- [ ] **Step 3: Service 작성 (browse + create, UNC/존재/디렉터리 검증)**

`apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceService.java`:
```java
package com.agentdock.backend.workspace;

import com.agentdock.backend.common.BadRequestException;
import com.agentdock.backend.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository repository;
    private final WorkspaceFs workspaceFs;

    public List<WorkspaceResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(WorkspaceResponse::from).toList();
    }

    public WorkspaceResponse create(CreateWorkspaceRequest request) {
        if (workspaceFs.isUncPath(request.path())) {
            throw new BadRequestException("Network path (UNC) is not allowed");
        }
        File dir = new File(request.path()).getAbsoluteFile();
        if (!dir.exists()) {
            throw new BadRequestException("Path \"" + dir.getPath() + "\" does not exist");
        }
        if (!dir.isDirectory()) {
            throw new BadRequestException("Path \"" + dir.getPath() + "\" is not a directory");
        }
        Workspace workspace = new Workspace();
        workspace.setName(request.name());
        workspace.setPath(dir.getAbsolutePath());
        workspace.setDescription(request.description());
        return WorkspaceResponse.from(repository.save(workspace));
    }

    public WorkspaceBrowseResult browse(String path) {
        if (path == null || path.isBlank()) {
            return new WorkspaceBrowseResult(null, null, workspaceFs.listRoots());
        }
        if (workspaceFs.isUncPath(path)) {
            throw new BadRequestException("Network path (UNC) is not allowed");
        }
        File dir = new File(path).getAbsoluteFile();
        if (!dir.exists()) {
            throw new NotFoundException("Path \"" + dir.getPath() + "\" does not exist");
        }
        if (!dir.isDirectory()) {
            throw new BadRequestException("Path \"" + dir.getPath() + "\" is not a directory");
        }
        File[] children = dir.listFiles(File::isDirectory);
        List<FsEntry> entries = children == null ? List.of() :
                Arrays.stream(children)
                        .map(f -> new FsEntry(f.getName(), f.getAbsolutePath()))
                        .sorted(Comparator.comparing(FsEntry::name))
                        .toList();
        File parent = dir.getParentFile();
        String parentPath = parent == null ? null : parent.getAbsolutePath();
        return new WorkspaceBrowseResult(dir.getAbsolutePath(), parentPath, entries);
    }
}
```

- [ ] **Step 4: Controller 작성**

`apps/backend/src/main/java/com/agentdock/backend/workspace/WorkspaceController.java`:
```java
package com.agentdock.backend.workspace;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService service;

    @GetMapping
    public List<WorkspaceResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/browse")
    public WorkspaceBrowseResult browse(@RequestParam(required = false) String path) {
        return service.browse(path);
    }

    @PostMapping
    public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return service.create(request);
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: DB 기동 후 UNC/존재하지 않는 경로 검증 (Review Focus 항목)**

애플리케이션을 아직 전체 기동할 수는 없으므로(Agent/Execution 모듈 미완성) 이 검증은 Task 12(백엔드 전체 E2E)에서 함께 실행한다. 여기서는 컴파일 통과만 확인하고 다음 태스크로 진행한다.

- [ ] **Step 7: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/workspace
git commit -m "feat(backend): Workspace 모듈(폴더 브라우징, UNC 차단) 포팅"
```

---

## Task 9: Runtime 계층 (AgentRuntime, ClaudeCodeRuntime, RuntimeRegistry, ProcessService)

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/runtime/AgentExecutionRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/runtime/AgentExecutionResult.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/runtime/AgentRuntime.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/process/ProcessService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/runtime/ClaudeCodeRuntime.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/runtime/RuntimeRegistry.java`

**Interfaces:**
- Produces: `AgentRuntime` 인터페이스(`getProviderKey()`, `execute(AgentExecutionRequest): AgentExecutionResult`, `cancel(String)`), `RuntimeRegistry.resolve(String providerKey): AgentRuntime` — Task 11(Execution 모듈)이 이 둘을 사용

- [ ] **Step 1: AgentRuntime 인터페이스 + 요청/결과 record 작성**

`apps/backend/src/main/java/com/agentdock/backend/runtime/AgentExecutionRequest.java`:
```java
package com.agentdock.backend.runtime;

import java.util.function.BiConsumer;

public record AgentExecutionRequest(
        String executionId,
        String prompt,
        String workspacePath,
        String model,
        String mode,
        BiConsumer<String, String> onLog
) {}
```

`apps/backend/src/main/java/com/agentdock/backend/runtime/AgentExecutionResult.java`:
```java
package com.agentdock.backend.runtime;

public record AgentExecutionResult(int exitCode) {}
```

`apps/backend/src/main/java/com/agentdock/backend/runtime/AgentRuntime.java`:
```java
package com.agentdock.backend.runtime;

/**
 * Agent와 실제 AI 실행 엔진(Claude Code, Codex 등)을 분리하는 경계.
 * 새 Runtime을 추가할 때는 이 인터페이스만 구현하고 Spring Bean으로 등록하면 된다.
 */
public interface AgentRuntime {
    String getProviderKey();
    AgentExecutionResult execute(AgentExecutionRequest request) throws Exception;
    void cancel(String executionId);
}
```

- [ ] **Step 2: ProcessService (ProcessBuilder 래퍼) 작성**

`apps/backend/src/main/java/com/agentdock/backend/process/ProcessService.java`:
```java
package com.agentdock.backend.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 CLI(Claude Code, Codex 등) 프로세스 실행을 담당한다.
 * ProcessBuilder는 shell을 경유하지 않으므로 사용자 prompt가 셸 명령으로 해석되지 않는다.
 */
@Service
@Slf4j
public class ProcessService {
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public Process spawn(String executionId, String command, List<String> args, String cwd) {
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);
            ProcessBuilder builder = new ProcessBuilder(fullCommand);
            builder.directory(new File(cwd));
            builder.environment().putAll(System.getenv());
            Process process = builder.start();
            processes.put(executionId, process);
            process.onExit().thenRun(() -> processes.remove(executionId));
            log.info("spawned execution={} command={}", executionId, command);
            return process;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to spawn process for execution " + executionId, ex);
        }
    }

    public boolean cancel(String executionId) {
        Process process = processes.get(executionId);
        if (process == null) {
            return false;
        }
        process.destroy();
        return true;
    }

    public boolean isRunning(String executionId) {
        return processes.containsKey(executionId);
    }
}
```

- [ ] **Step 3: ClaudeCodeRuntime 작성**

`apps/backend/src/main/java/com/agentdock/backend/runtime/ClaudeCodeRuntime.java`:
```java
package com.agentdock.backend.runtime;

import com.agentdock.backend.process.ProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Claude Code CLI를 비대화형(print) 모드로 실행하는 Runtime 구현체.
 * 실행 바이너리는 환경변수 CLAUDE_CODE_BIN으로 override 가능하다(기본값 claude).
 */
@Component
@RequiredArgsConstructor
public class ClaudeCodeRuntime implements AgentRuntime {
    private final ProcessService processService;

    @Override
    public String getProviderKey() {
        return "CLAUDE_CODE";
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws Exception {
        String command = System.getenv().getOrDefault("CLAUDE_CODE_BIN", "claude");
        List<String> args = new ArrayList<>(List.of("-p", request.prompt(), "--output-format", "text"));
        if (request.model() != null) {
            args.add("--model");
            args.add(request.model());
        }

        Process process = processService.spawn(request.executionId(), command, args, request.workspacePath());

        CompletableFuture<Void> stdout = CompletableFuture.runAsync(() -> pump(process.getInputStream(), "stdout", request));
        CompletableFuture<Void> stderr = CompletableFuture.runAsync(() -> pump(process.getErrorStream(), "stderr", request));

        int exitCode = process.waitFor();
        CompletableFuture.allOf(stdout, stderr).join();
        return new AgentExecutionResult(exitCode);
    }

    private void pump(InputStream input, String stream, AgentExecutionRequest request) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                request.onLog().accept(line + "\n", stream);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void cancel(String executionId) {
        processService.cancel(executionId);
    }
}
```

- [ ] **Step 4: RuntimeRegistry 작성**

`apps/backend/src/main/java/com/agentdock/backend/runtime/RuntimeRegistry.java`:
```java
package com.agentdock.backend.runtime;

import com.agentdock.backend.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * provider key -> AgentRuntime 구현체 매핑.
 * 새 Runtime(Codex, Gemini 등)을 추가할 때는 AgentRuntime을 구현하고 @Component로 등록만 하면 되고,
 * 나머지 코드는 건드리지 않는다.
 */
@Component
public class RuntimeRegistry {
    private final Map<String, AgentRuntime> runtimes;

    public RuntimeRegistry(List<AgentRuntime> runtimeBeans) {
        this.runtimes = runtimeBeans.stream()
                .collect(Collectors.toMap(AgentRuntime::getProviderKey, r -> r));
    }

    public AgentRuntime resolve(String providerKey) {
        AgentRuntime runtime = runtimes.get(providerKey);
        if (runtime == null) {
            throw new NotFoundException("No AgentRuntime registered for provider \"" + providerKey + "\"");
        }
        return runtime;
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/runtime apps/backend/src/main/java/com/agentdock/backend/process
git commit -m "feat(backend): AgentRuntime/ClaudeCodeRuntime/RuntimeRegistry/ProcessService 포팅"
```

---

## Task 10: Agent 모듈

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/CreateAgentRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/AgentResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/AgentService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/agent/AgentController.java`

**Interfaces:**
- Consumes: `AgentRepository.findAllWithRelations/findByIdWithRelations` (Task 4), `AgentRoleRepository`/`PermissionProfileRepository`/`AiProviderRepository`/`AiConnectionRepository`/`WorkspaceRepository` (Task 4), `AgentRoleResponse.from` (Task 6), `PermissionProfileResponse.from` (Task 7), `AiProviderSummary.from`/`AiConnectionSummary.from` (Task 5), `WorkspaceResponse.from` (Task 8)
- Produces: `AgentResponse.from(Agent)` — Task 11(Execution 모듈)이 Agent 엔티티 자체(응답 DTO 아님)를 조회해 사용

- [ ] **Step 1: 요청 DTO 작성**

`apps/backend/src/main/java/com/agentdock/backend/agent/CreateAgentRequest.java`:
```java
package com.agentdock.backend.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateAgentRequest(
        @NotBlank String name,
        @NotNull Long roleId,
        @NotNull Long permissionProfileId,
        @NotNull Long providerId,
        Long connectionId,
        Long workspaceId,
        String model,
        String mode,
        Map<String, Object> profile
) {}
```

- [ ] **Step 2: 응답 DTO 작성 (다른 모듈의 Summary/Response record 재사용, 순환 참조 없음)**

`apps/backend/src/main/java/com/agentdock/backend/agent/AgentResponse.java`:
```java
package com.agentdock.backend.agent;

import com.agentdock.backend.permission.PermissionProfileResponse;
import com.agentdock.backend.provider.AiConnectionSummary;
import com.agentdock.backend.provider.AiProviderSummary;
import com.agentdock.backend.role.AgentRoleResponse;
import com.agentdock.backend.workspace.WorkspaceResponse;

import java.time.Instant;
import java.util.Map;

public record AgentResponse(
        Long id, String name,
        Long roleId, AgentRoleResponse role,
        Long permissionProfileId, PermissionProfileResponse permissionProfile,
        Long providerId, AiProviderSummary provider,
        Long connectionId, AiConnectionSummary connection,
        Long workspaceId, WorkspaceResponse workspace,
        String model, String mode, Map<String, Object> profile,
        Instant createdAt, Instant updatedAt
) {
    public static AgentResponse from(Agent a) {
        return new AgentResponse(
                a.getId(), a.getName(),
                a.getRoleId(), AgentRoleResponse.from(a.getRole()),
                a.getPermissionProfileId(), PermissionProfileResponse.from(a.getPermissionProfile()),
                a.getProviderId(), AiProviderSummary.from(a.getProvider()),
                a.getConnectionId(), a.getConnection() == null ? null : AiConnectionSummary.from(a.getConnection()),
                a.getWorkspaceId(), a.getWorkspace() == null ? null : WorkspaceResponse.from(a.getWorkspace()),
                a.getModel(), a.getMode(), a.getProfile(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 3: Service 작성**

`apps/backend/src/main/java/com/agentdock/backend/agent/AgentService.java`:
```java
package com.agentdock.backend.agent;

import com.agentdock.backend.common.NotFoundException;
import com.agentdock.backend.permission.PermissionProfileRepository;
import com.agentdock.backend.provider.AiConnectionRepository;
import com.agentdock.backend.provider.AiProviderRepository;
import com.agentdock.backend.role.AgentRoleRepository;
import com.agentdock.backend.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentRoleRepository roleRepository;
    private final PermissionProfileRepository permissionProfileRepository;
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;
    private final WorkspaceRepository workspaceRepository;

    public List<AgentResponse> findAll() {
        return agentRepository.findAllWithRelations().stream().map(AgentResponse::from).toList();
    }

    public AgentResponse findOne(Long id) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        return AgentResponse.from(agent);
    }

    public AgentResponse create(CreateAgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.name());
        agent.setRole(roleRepository.findById(request.roleId())
                .orElseThrow(() -> new NotFoundException("AgentRole %d not found".formatted(request.roleId()))));
        agent.setPermissionProfile(permissionProfileRepository.findById(request.permissionProfileId())
                .orElseThrow(() -> new NotFoundException("PermissionProfile %d not found".formatted(request.permissionProfileId()))));
        agent.setProvider(providerRepository.findById(request.providerId())
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(request.providerId()))));
        if (request.connectionId() != null) {
            agent.setConnection(connectionRepository.findById(request.connectionId())
                    .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(request.connectionId()))));
        }
        if (request.workspaceId() != null) {
            agent.setWorkspace(workspaceRepository.findById(request.workspaceId())
                    .orElseThrow(() -> new NotFoundException("Workspace %d not found".formatted(request.workspaceId()))));
        }
        agent.setModel(request.model());
        agent.setMode(request.mode());
        agent.setProfile(request.profile());
        Agent saved = agentRepository.save(agent);
        return findOne(saved.getId());
    }
}
```

- [ ] **Step 4: Controller 작성**

`apps/backend/src/main/java/com/agentdock/backend/agent/AgentController.java`:
```java
package com.agentdock.backend.agent;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService service;

    @GetMapping
    public List<AgentResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AgentResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    public AgentResponse create(@Valid @RequestBody CreateAgentRequest request) {
        return service.create(request);
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/agent
git commit -m "feat(backend): Agent 모듈 포팅"
```

---

## Task 11: Execution 모듈 (+ SSE, Permission Enforcement)

**Files:**
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/CreateExecutionRequest.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionLogResponse.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionService.java`
- Create: `apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionController.java`

**Interfaces:**
- Consumes: `AgentRepository.findByIdWithRelations` (Task 4), `PermissionService.isAllowed` (Task 7), `RuntimeRegistry.resolve` (Task 9)
- Produces: 없음 (최상위 모듈, 이후 아무도 이 모듈을 참조하지 않는다)

- [ ] **Step 1: 요청/응답 DTO 작성**

`apps/backend/src/main/java/com/agentdock/backend/execution/CreateExecutionRequest.java`:
```java
package com.agentdock.backend.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExecutionRequest(@NotNull Long agentId, @NotBlank String prompt) {}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionResponse.java`:
```java
package com.agentdock.backend.execution;

import java.time.Instant;

public record ExecutionResponse(
        Long id, Long agentId, Long workspaceId, String prompt, ExecutionStatus status,
        Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage,
        Instant createdAt, Instant updatedAt
) {
    public static ExecutionResponse from(Execution e) {
        return new ExecutionResponse(e.getId(), e.getAgentId(), e.getWorkspaceId(), e.getPrompt(), e.getStatus(),
                e.getStartedAt(), e.getFinishedAt(), e.getExitCode(), e.getErrorMessage(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
```

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionLogResponse.java`:
```java
package com.agentdock.backend.execution;

import java.time.Instant;

public record ExecutionLogResponse(Long id, Long executionId, LogStream stream, String content, Instant createdAt) {
    public static ExecutionLogResponse from(ExecutionLog log) {
        return new ExecutionLogResponse(log.getId(), log.getExecutionId(), log.getStream(), log.getContent(), log.getCreatedAt());
    }
}
```

- [ ] **Step 2: Service 작성 (Permission Enforcement + 비동기 실행 + SSE fan-out)**

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionService.java`:
```java
package com.agentdock.backend.execution;

import com.agentdock.backend.agent.Agent;
import com.agentdock.backend.agent.AgentRepository;
import com.agentdock.backend.common.BadRequestException;
import com.agentdock.backend.common.ForbiddenException;
import com.agentdock.backend.common.NotFoundException;
import com.agentdock.backend.permission.PermissionAction;
import com.agentdock.backend.permission.PermissionService;
import com.agentdock.backend.runtime.AgentExecutionRequest;
import com.agentdock.backend.runtime.AgentRuntime;
import com.agentdock.backend.runtime.RuntimeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final AgentRepository agentRepository;
    private final RuntimeRegistry runtimeRegistry;
    private final PermissionService permissionService;

    private final Map<String, List<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ExecutionResponse findOne(Long id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(id)));
        return ExecutionResponse.from(execution);
    }

    public List<ExecutionLogResponse> getLogs(Long executionId) {
        return logRepository.findByExecutionIdOrderByCreatedAtAsc(executionId).stream()
                .map(ExecutionLogResponse::from).toList();
    }

    public ExecutionResponse create(Long agentId, String prompt) {
        Agent agent = agentRepository.findByIdWithRelations(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));

        if (agent.getWorkspace() == null) {
            throw new BadRequestException("Agent has no workspace assigned");
        }
        // Permission Enforcement: Prompt 설명이 아니라 실행 전 Backend에서 실제로 차단한다.
        if (!permissionService.isAllowed(agent.getPermissionProfile(), PermissionAction.TERMINAL_EXECUTE)) {
            throw new ForbiddenException("Agent permission profile does not allow TERMINAL_EXECUTE");
        }

        Execution execution = new Execution();
        execution.setAgent(agent);
        execution.setWorkspace(agent.getWorkspace());
        execution.setPrompt(prompt);
        execution.setStatus(ExecutionStatus.PENDING);
        Execution saved = executionRepository.save(execution);

        executor.submit(() -> run(saved.getId(), agent, prompt));

        return ExecutionResponse.from(saved);
    }

    public SseEmitter streamLogs(String executionId) {
        List<SseEmitter> emitters = streams.get(executionId);
        SseEmitter emitter = new SseEmitter(0L);
        if (emitters == null) {
            emitter.complete();
            return emitter;
        }
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    public Map<String, Boolean> cancel(Long id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(id)));
        Agent agent = agentRepository.findByIdWithRelations(execution.getAgentId())
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(execution.getAgentId())));
        AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
        runtime.cancel(String.valueOf(execution.getId()));
        return Map.of("cancelled", true);
    }

    private void run(Long id, Agent agent, String prompt) {
        String streamKey = String.valueOf(id);
        streams.put(streamKey, new CopyOnWriteArrayList<>());

        updateStatus(id, ExecutionStatus.RUNNING, Instant.now(), null, null, null);

        try {
            AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
            var result = runtime.execute(new AgentExecutionRequest(
                    streamKey, prompt, agent.getWorkspace().getPath(), agent.getModel(), agent.getMode(),
                    (chunk, stream) -> {
                        broadcast(streamKey, stream, chunk);
                        persistLog(id, stream, chunk);
                    }
            ));
            ExecutionStatus finalStatus = result.exitCode() == 0 ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
            updateStatus(id, finalStatus, null, Instant.now(), result.exitCode(), null);
        } catch (Exception ex) {
            log.error("execution {} failed", id, ex);
            updateStatus(id, ExecutionStatus.FAILED, null, Instant.now(), null, String.valueOf(ex));
        } finally {
            List<SseEmitter> emitters = streams.remove(streamKey);
            if (emitters != null) {
                emitters.forEach(SseEmitter::complete);
            }
        }
    }

    private void updateStatus(Long id, ExecutionStatus status, Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage) {
        Execution execution = executionRepository.findById(id).orElseThrow();
        execution.setStatus(status);
        if (startedAt != null) execution.setStartedAt(startedAt);
        if (finishedAt != null) execution.setFinishedAt(finishedAt);
        if (exitCode != null) execution.setExitCode(exitCode);
        if (errorMessage != null) execution.setErrorMessage(errorMessage);
        executionRepository.save(execution);
    }

    private void broadcast(String streamKey, String stream, String content) {
        List<SseEmitter> emitters = streams.get(streamKey);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(Map.of("stream", stream, "content", content)));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private void persistLog(Long executionId, String stream, String content) {
        ExecutionLog logEntry = new ExecutionLog();
        logEntry.setExecution(executionRepository.getReferenceById(executionId));
        logEntry.setStream(stream.equalsIgnoreCase("stderr") ? LogStream.STDERR : LogStream.STDOUT);
        logEntry.setContent(content);
        logRepository.save(logEntry);
    }
}
```

- [ ] **Step 3: Controller 작성**

`apps/backend/src/main/java/com/agentdock/backend/execution/ExecutionController.java`:
```java
package com.agentdock.backend.execution;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/executions")
@RequiredArgsConstructor
public class ExecutionController {
    private final ExecutionService service;

    @PostMapping
    public ExecutionResponse create(@Valid @RequestBody CreateExecutionRequest request) {
        return service.create(request.agentId(), request.prompt());
    }

    @GetMapping("/{id}")
    public ExecutionResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @GetMapping("/{id}/logs")
    public List<ExecutionLogResponse> getLogs(@PathVariable Long id) {
        return service.getLogs(id);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        return service.streamLogs(id);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Boolean> cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd apps/backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/java/com/agentdock/backend/execution
git commit -m "feat(backend): Execution 모듈(SSE, Permission Enforcement) 포팅"
```

---

## Task 12: 백엔드 전체 E2E 검증 (테스트 포트 8081)

**Files:** 없음(검증 전용 태스크)

**Interfaces:**
- Consumes: Task 1~11 전체

- [ ] **Step 1: 로컬 DB 준비 확인**

```bash
psql -U postgres -lqt | grep AGENT_DOCK
```
Expected: `AGENT_DOCK` 존재(Task 2 Step 2에서 이미 비워둔 상태). 없으면 `psql -U postgres -c 'CREATE DATABASE "AGENT_DOCK";'`

- [ ] **Step 2: 테스트 포트(8081)로 기동, Flyway 자동 적용 확인**

```bash
cd apps/backend
PORT=8081 ./gradlew bootRun
```
Expected: 로그에 `Flyway Community Edition` 마이그레이션 적용 로그와 `Started BackendApplication` 출력. `psql -U postgres -d AGENT_DOCK -c '\dt'`로 8개 테이블 생성 확인.

- [ ] **Step 3: 전체 체인 curl 검증**

```bash
curl -s -X POST http://localhost:8081/ai-providers -H 'Content-Type: application/json' -d '{"key":"CLAUDE_CODE","name":"claude"}'
curl -s -X POST http://localhost:8081/roles -H 'Content-Type: application/json' -d '{"name":"Backend Dev"}'
curl -s -X POST http://localhost:8081/permission-profiles -H 'Content-Type: application/json' -d '{"name":"Default","fileRead":true,"fileWrite":true,"terminalExecute":true}'
curl -s -X POST http://localhost:8081/workspaces -H 'Content-Type: application/json' -d '{"name":"TestWS","path":"'$(pwd)'"}'
```
각 응답에서 `id`(정수 1부터 시작)를 확인하고, 아래 명령의 `roleId`/`permissionProfileId`/`providerId`/`workspaceId`에 대입:
```bash
curl -s -X POST http://localhost:8081/agents -H 'Content-Type: application/json' -d '{"name":"Agent1","roleId":1,"permissionProfileId":1,"providerId":1,"workspaceId":1}'
```
Expected: 응답에 `role`, `permissionProfile`, `provider`, `workspace` 중첩 객체와 `roleId` 등 평평한 FK가 함께 존재(Review Focus 4번 — StackOverflow 없이 정상 JSON).

```bash
curl -s http://localhost:8081/agents/1
curl -s http://localhost:8081/agents/abc
```
Expected: 첫 번째는 200, 두 번째는 `{"statusCode":400,...}` (Review Focus 2번).

- [ ] **Step 4: Permission Enforcement 검증 (Review Focus 3번)**

`terminalExecute:false`인 프로필로 새 Agent를 만들어 Execution 시도:
```bash
curl -s -X POST http://localhost:8081/permission-profiles -H 'Content-Type: application/json' -d '{"name":"NoExec"}'
curl -s -X POST http://localhost:8081/agents -H 'Content-Type: application/json' -d '{"name":"Agent2","roleId":1,"permissionProfileId":2,"providerId":1,"workspaceId":1}'
curl -s -w '\n%{http_code}\n' -X POST http://localhost:8081/executions -H 'Content-Type: application/json' -d '{"agentId":2,"prompt":"echo hi"}'
```
Expected: HTTP 403, `claude` CLI가 spawn되지 않음(로그에 spawn 관련 로그 없음).

- [ ] **Step 5: Workspace 경로 검증 (Review Focus 1, 5번)**

```bash
curl -s -w '\n%{http_code}\n' -X POST http://localhost:8081/workspaces -H 'Content-Type: application/json' -d '{"name":"UNC","path":"\\\\server\\share"}'
curl -s -w '\n%{http_code}\n' -X POST http://localhost:8081/workspaces -H 'Content-Type: application/json' -d '{"name":"NoExist","path":"/no/such/path"}'
curl -s http://localhost:8081/workspaces/browse
```
Expected: 앞의 둘은 400, 마지막은 OS 드라이브/루트 목록 반환.

- [ ] **Step 6: 정상 Execution 실행(선택 — claude CLI 있으면)**

```bash
curl -s -X POST http://localhost:8081/executions -H 'Content-Type: application/json' -d '{"agentId":1,"prompt":"echo hi"}'
curl -s http://localhost:8081/executions/2
curl -s http://localhost:8081/executions/2/logs
```
Expected: `claude` CLI가 PATH에 없다면 `status: FAILED`라도 무방(런타임 자체가 정상 spawn/에러 캡처했는지가 검증 대상). `2/logs`에 stderr/stdout 로그가 저장되어 있으면 SSE/영속화 파이프라인 정상.

- [ ] **Step 7: 테스트 데이터 정리 + 서버 종료**

```bash
psql -U postgres -d AGENT_DOCK -c 'TRUNCATE execution_log, execution, agent, workspace, permission_profile, agent_role, ai_connection, ai_provider RESTART IDENTITY CASCADE;'
```
`bootRun` 프로세스를 Ctrl+C로 종료(또는 `kill`). `agents/CONVENTIONS.md`의 포트 규칙에 따라 8081을 계속 띄워두지 않는다.

- [ ] **Step 8: 이 태스크는 코드 변경이 없으므로 커밋하지 않는다.**

---

## Task 13: 프론트엔드 스캐폴딩 — 기존 Next.js 삭제, Vite+React 프로젝트 생성

**Files:**
- Delete: `apps/frontend/` 전체(기존 Next.js 프로젝트)
- Create: `apps/frontend/package.json`
- Create: `apps/frontend/vite.config.ts`
- Create: `apps/frontend/tsconfig.json`
- Create: `apps/frontend/index.html`
- Create: `apps/frontend/src/vite-env.d.ts`
- Create: `apps/frontend/src/main.tsx`
- Create: `apps/frontend/src/lib/api.ts`
- Create: `apps/frontend/src/globals.css` (기존 `apps/frontend`(구) `src/app/globals.css`와 동일 내용)
- Create: `apps/frontend/src/layout.module.css` (기존 `src/app/layout.module.css`와 동일 내용)
- Create: `apps/frontend/.env.local`

**Interfaces:**
- Produces: `api` 객체(Task 14~17이 전부 이 모듈에서 import), `App.tsx`는 Task 17에서 최종 조립(지금은 페이지가 없으므로 라우트 정의는 마지막에 완성)

- [ ] **Step 1: 재사용할 CSS를 먼저 백업한 뒤 기존 apps/frontend 삭제**

CSS Modules 파일들은 내용 변경 없이 새 위치로 옮겨 쓸 것이므로, 삭제하기 전에 임시 디렉터리로 복사해둔다(삭제 후에는 git 히스토리에서 커밋 해시를 알아내 꺼내야 해서 번거롭고 실수하기 쉽다):

```bash
mkdir -p apps/frontend-assets-tmp
cp apps/frontend/src/app/globals.css apps/frontend-assets-tmp/globals.css
cp apps/frontend/src/app/layout.module.css apps/frontend-assets-tmp/layout.module.css
cp apps/frontend/src/app/page.module.css apps/frontend-assets-tmp/Dashboard.module.css
cp apps/frontend/src/app/agents/page.module.css apps/frontend-assets-tmp/Agents.module.css
cp "apps/frontend/src/app/executions/[id]/page.module.css" apps/frontend-assets-tmp/ExecutionDetail.module.css
cp apps/frontend/src/app/workspaces/page.module.css apps/frontend-assets-tmp/Workspaces.page.module.css
cp apps/frontend/src/app/workspaces/WorkspacePicker.module.css apps/frontend-assets-tmp/WorkspacePicker.module.css
```

이제 기존 프로젝트를 삭제한다(`apps/frontend-assets-tmp`는 `apps/frontend` 밖에 있으므로 영향받지 않는다):
```bash
git rm -r apps/frontend
```

- [ ] **Step 2: package.json, vite.config.ts, tsconfig.json 작성**

`apps/frontend/package.json`:
```json
{
  "name": "@agentdock/frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite --port 3030",
    "dev:test": "vite --port 3031",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.2"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.2",
    "typescript": "^5.6.3",
    "vite": "^5.4.8"
  }
}
```

`apps/frontend/vite.config.ts`:
```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
});
```

`apps/frontend/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true
  },
  "include": ["src"]
}
```

- [ ] **Step 3: index.html, main.tsx, vite-env.d.ts 작성**

`apps/frontend/index.html`:
```html
<!doctype html>
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <title>AgentDock</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

`apps/frontend/src/vite-env.d.ts`:
```ts
/// <reference types="vite/client" />
```

`apps/frontend/src/main.tsx`:
```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './globals.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
```

- [ ] **Step 4: api.ts 포팅 (Long id → number, NEXT_PUBLIC_ → VITE_)**

`apps/frontend/src/lib/api.ts`:
```ts
const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    cache: 'no-store',
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  return res.json() as Promise<T>;
}

export interface AgentRole {
  id: number;
  name: string;
  description?: string | null;
}

export interface PermissionProfile {
  id: number;
  name: string;
}

export interface AiProvider {
  id: number;
  key: string;
  name: string;
}

export interface Workspace {
  id: number;
  name: string;
  path: string;
}

export interface Agent {
  id: number;
  name: string;
  role: AgentRole;
  permissionProfile: PermissionProfile;
  provider: AiProvider;
  workspace: Workspace | null;
  model: string | null;
  mode: string | null;
}

export interface Execution {
  id: number;
  status: string;
  prompt: string;
  exitCode: number | null;
}

export interface WorkspaceBrowseEntry {
  name: string;
  path: string;
}

export interface WorkspaceBrowseResult {
  path: string | null;
  parentPath: string | null;
  entries: WorkspaceBrowseEntry[];
}

export const api = {
  base: API_BASE,
  listRoles: () => request<AgentRole[]>('/roles'),
  createRole: (data: { name: string; description?: string }) =>
    request<AgentRole>('/roles', { method: 'POST', body: JSON.stringify(data) }),

  listPermissionProfiles: () => request<PermissionProfile[]>('/permission-profiles'),
  createPermissionProfile: (data: Record<string, unknown>) =>
    request<PermissionProfile>('/permission-profiles', { method: 'POST', body: JSON.stringify(data) }),

  listProviders: () => request<AiProvider[]>('/ai-providers'),
  createProvider: (data: { key: string; name: string }) =>
    request<AiProvider>('/ai-providers', { method: 'POST', body: JSON.stringify(data) }),

  listWorkspaces: () => request<Workspace[]>('/workspaces'),
  createWorkspace: (data: { name: string; path: string; description?: string }) =>
    request<Workspace>('/workspaces', { method: 'POST', body: JSON.stringify(data) }),
  browseWorkspace: (path?: string) =>
    request<WorkspaceBrowseResult>(`/workspaces/browse${path ? `?path=${encodeURIComponent(path)}` : ''}`),

  listAgents: () => request<Agent[]>('/agents'),
  createAgent: (data: Record<string, unknown>) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),

  createExecution: (data: { agentId: number; prompt: string }) =>
    request<Execution>('/executions', { method: 'POST', body: JSON.stringify(data) }),
  getExecution: (id: number | string) => request<Execution>(`/executions/${id}`),
};
```

- [ ] **Step 5: 전역 CSS 이식 (Step 1에서 백업해둔 파일 사용)**

```bash
mkdir -p apps/frontend/src
cp apps/frontend-assets-tmp/globals.css apps/frontend/src/globals.css
cp apps/frontend-assets-tmp/layout.module.css apps/frontend/src/layout.module.css
```

- [ ] **Step 6: .env.local 작성**

`apps/frontend/.env.local`:
```
VITE_API_BASE=http://localhost:8080
```

- [ ] **Step 7: 의존성 설치 및 타입체크(페이지가 아직 없으므로 App.tsx 없이는 실패하는 게 정상 — 최소 placeholder App.tsx로 확인만)**

임시로 아래 내용의 `apps/frontend/src/App.tsx`를 만들어 설치/타입체크만 확인한다(Task 17에서 최종본으로 덮어쓴다):
```tsx
export default function App() {
  return <div>loading...</div>;
}
```
```bash
cd apps/frontend
npm install
npx tsc -b --noEmit
```
Expected: 에러 없음.

- [ ] **Step 8: 커밋**

```bash
git add apps/frontend
git commit -m "feat(frontend): Vite+React 프로젝트 스캐폴딩, api.ts 포팅"
```

---

## Task 14: Dashboard + Providers 페이지

**Files:**
- Create: `apps/frontend/src/pages/Dashboard.tsx`
- Create: `apps/frontend/src/pages/Dashboard.module.css`
- Create: `apps/frontend/src/pages/Providers.tsx`

**Interfaces:**
- Consumes: `api` (Task 13)

- [ ] **Step 1: Dashboard 페이지 작성**

Task 13 Step 1에서 백업해둔 CSS를 가져온다:
```bash
mkdir -p apps/frontend/src/pages
cp apps/frontend-assets-tmp/Dashboard.module.css apps/frontend/src/pages/Dashboard.module.css
```

`apps/frontend/src/pages/Dashboard.tsx`:
```tsx
import styles from './Dashboard.module.css';

export default function Dashboard() {
  return (
    <div>
      <h1>AgentDock</h1>
      <p>멀티 에이전트 조직 운영 플랫폼 — Phase 1 MVP</p>
      <ul className={styles.list}>
        <li><a href="/providers">AI Provider 등록</a></li>
        <li><a href="/workspaces">Workspace 등록</a></li>
        <li><a href="/agents">Agent 생성 및 실행</a></li>
      </ul>
    </div>
  );
}
```

- [ ] **Step 2: Providers 페이지 작성**

`apps/frontend/src/pages/Providers.tsx`:
```tsx
import { FormEvent, useEffect, useState } from 'react';
import { AiProvider, api } from '../lib/api';

const PROVIDER_KEYS = ['CLAUDE_CODE', 'CODEX', 'COMMAND_CODE', 'GEMINI'];

export default function Providers() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [key, setKey] = useState(PROVIDER_KEYS[0]);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = () => api.listProviders().then(setProviders).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createProvider({ key, name });
      setName('');
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>AI Providers / Connections</h1>
      <form onSubmit={onSubmit} className="formRow">
        <select value={key} onChange={(e) => setKey(e.target.value)}>
          {PROVIDER_KEYS.map((k) => (
            <option key={k} value={k}>
              {k}
            </option>
          ))}
        </select>
        <input placeholder="표시 이름 (예: Claude Code)" value={name} onChange={(e) => setName(e.target.value)} required />
        <button type="submit">등록</button>
      </form>
      {error && <p className="errorText">{error}</p>}
      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Key</th>
            <th>Name</th>
            <th>Connections</th>
          </tr>
        </thead>
        <tbody>
          {providers.map((p) => (
            <tr key={p.id}>
              <td>{p.key}</td>
              <td>{p.name}</td>
              <td>{(p as any).connections?.length ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크**

Run: `cd apps/frontend && npx tsc -b --noEmit`
Expected: 에러 없음 (아직 `App.tsx`가 Task 13의 placeholder라 이 두 페이지는 import되지 않지만, 파일 자체의 타입 오류는 잡힌다)

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/src/pages/Dashboard.tsx apps/frontend/src/pages/Dashboard.module.css apps/frontend/src/pages/Providers.tsx
git commit -m "feat(frontend): Dashboard, Providers 페이지 포팅"
```

---

## Task 15: Workspaces 페이지 + WorkspacePicker

**Files:**
- Create: `apps/frontend/src/pages/Workspaces/Workspaces.tsx`
- Create: `apps/frontend/src/pages/Workspaces/WorkspacePicker.tsx`
- Create: `apps/frontend/src/pages/Workspaces/page.module.css`
- Create: `apps/frontend/src/pages/Workspaces/WorkspacePicker.module.css`

**Interfaces:**
- Consumes: `api.browseWorkspace`, `api.createWorkspace`, `api.listWorkspaces` (Task 13)

- [ ] **Step 1: CSS 이식**

Task 13 Step 1에서 백업해둔 CSS를 가져온다:
```bash
mkdir -p apps/frontend/src/pages/Workspaces
cp apps/frontend-assets-tmp/Workspaces.page.module.css apps/frontend/src/pages/Workspaces/page.module.css
cp apps/frontend-assets-tmp/WorkspacePicker.module.css apps/frontend/src/pages/Workspaces/WorkspacePicker.module.css
```

- [ ] **Step 2: WorkspacePicker.tsx 작성**

`apps/frontend/src/pages/Workspaces/WorkspacePicker.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { WorkspaceBrowseEntry, api } from '../../lib/api';
import styles from './WorkspacePicker.module.css';

interface WorkspacePickerProps {
  onSelect: (path: string) => void;
  onClose: () => void;
}

export default function WorkspacePicker({ onSelect, onClose }: WorkspacePickerProps) {
  const [currentPath, setCurrentPath] = useState<string | null>(null);
  const [parentPath, setParentPath] = useState<string | null>(null);
  const [entries, setEntries] = useState<WorkspaceBrowseEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = (path?: string) => {
    setError(null);
    api
      .browseWorkspace(path)
      .then((res) => {
        setCurrentPath(res.path);
        setParentPath(res.parentPath);
        setEntries(res.entries);
      })
      .catch((e) => setError(String(e)));
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2>Workspace 폴더 선택</h2>
        <p className={styles.currentPath}>{currentPath ?? '드라이브 목록'}</p>
        {error && <p className="errorText">{error}</p>}
        <ul className={styles.list}>
          {parentPath !== null && (
            <li>
              <button type="button" onClick={() => load(parentPath)} className={styles.entryButton}>
                .. (상위 폴더)
              </button>
            </li>
          )}
          {entries.map((entry) => (
            <li key={entry.path}>
              <button type="button" onClick={() => load(entry.path)} className={styles.entryButton}>
                {entry.name}
              </button>
            </li>
          ))}
        </ul>
        <div className={styles.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="button" disabled={!currentPath} onClick={() => currentPath && onSelect(currentPath)}>
            이 폴더 선택
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Workspaces.tsx 작성**

`apps/frontend/src/pages/Workspaces/Workspaces.tsx`:
```tsx
import { FormEvent, useEffect, useState } from 'react';
import { Workspace, api } from '../../lib/api';
import WorkspacePicker from './WorkspacePicker';
import styles from './page.module.css';

export default function Workspaces() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [name, setName] = useState('');
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const load = () => api.listWorkspaces().then(setWorkspaces).catch((e) => setError(String(e)));

  useEffect(() => {
    load();
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createWorkspace({ name, path });
      setName('');
      setPath('');
      load();
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>Workspaces</h1>
      <p>Agent가 작업할 로컬 폴더를 선택하세요.</p>
      <form onSubmit={onSubmit} className="formRow">
        <input placeholder="이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <input
          placeholder="폴더 선택 버튼으로 지정하세요"
          value={path}
          readOnly
          className={styles.pathInput}
          required
        />
        <button type="button" onClick={() => setPickerOpen(true)}>
          폴더 선택
        </button>
        <button type="submit">등록</button>
      </form>
      {error && <p className="errorText">{error}</p>}
      <ul className={styles.list}>
        {workspaces.map((w) => (
          <li key={w.id}>
            <strong>{w.name}</strong> — {w.path}
          </li>
        ))}
      </ul>
      {pickerOpen && (
        <WorkspacePicker
          onSelect={(selected) => {
            setPath(selected);
            setPickerOpen(false);
          }}
          onClose={() => setPickerOpen(false)}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 4: 타입체크**

Run: `cd apps/frontend && npx tsc -b --noEmit`
Expected: 에러 없음

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/src/pages/Workspaces
git commit -m "feat(frontend): Workspaces 페이지 + WorkspacePicker 포팅"
```

---

## Task 16: Agents 페이지

**Files:**
- Create: `apps/frontend/src/pages/Agents.tsx`
- Create: `apps/frontend/src/pages/Agents.module.css`

**Interfaces:**
- Consumes: `api` (Task 13), `react-router-dom`의 `useNavigate` (Task 13에서 설치한 `react-router-dom` 의존성)

- [ ] **Step 1: CSS 이식**

Task 13 Step 1에서 백업해둔 CSS를 가져온다:
```bash
cp apps/frontend-assets-tmp/Agents.module.css apps/frontend/src/pages/Agents.module.css
```

- [ ] **Step 2: Agents.tsx 작성 (Next.js `window.location.href` → React Router `useNavigate`)**

`apps/frontend/src/pages/Agents.tsx`:
```tsx
import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Agent, AgentRole, AiProvider, PermissionProfile, Workspace, api } from '../lib/api';
import styles from './Agents.module.css';

export default function Agents() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [roles, setRoles] = useState<AgentRole[]>([]);
  const [permissionProfiles, setPermissionProfiles] = useState<PermissionProfile[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState({
    name: '',
    roleId: '',
    permissionProfileId: '',
    providerId: '',
    workspaceId: '',
    model: '',
  });

  const loadAll = () => {
    api.listAgents().then(setAgents).catch((e) => setError(String(e)));
    api.listRoles().then(setRoles).catch(() => {});
    api.listPermissionProfiles().then(setPermissionProfiles).catch(() => {});
    api.listProviders().then(setProviders).catch(() => {});
    api.listWorkspaces().then(setWorkspaces).catch(() => {});
  };

  useEffect(() => {
    loadAll();
  }, []);

  const quickCreateRole = async () => {
    const name = window.prompt('Role 이름 (예: Backend Developer)');
    if (!name) return;
    await api.createRole({ name });
    loadAll();
  };

  const quickCreatePermissionProfile = async () => {
    const name = window.prompt('Permission Profile 이름 (예: Backend Developer Default)');
    if (!name) return;
    await api.createPermissionProfile({
      name,
      fileRead: true,
      fileWrite: true,
      terminalExecute: true,
      gitDiff: true,
    });
    loadAll();
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createAgent({
        ...form,
        roleId: Number(form.roleId),
        permissionProfileId: Number(form.permissionProfileId),
        providerId: Number(form.providerId),
        workspaceId: form.workspaceId ? Number(form.workspaceId) : undefined,
        model: form.model || undefined,
      });
      setForm({ name: '', roleId: '', permissionProfileId: '', providerId: '', workspaceId: '', model: '' });
      loadAll();
    } catch (e) {
      setError(String(e));
    }
  };

  const runAgent = async (agentId: number) => {
    const promptText = window.prompt('Agent에게 전달할 Prompt를 입력하세요.');
    if (!promptText) return;
    try {
      const execution = await api.createExecution({ agentId, prompt: promptText });
      navigate(`/executions/${execution.id}`);
    } catch (e) {
      setError(String(e));
    }
  };

  return (
    <div>
      <h1>Agents</h1>

      <form onSubmit={onSubmit} className={styles.form}>
        <input
          placeholder="Agent 이름 (예: Backend Developer A)"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />

        <div className="formRow">
          <select value={form.roleId} onChange={(e) => setForm({ ...form, roleId: e.target.value })} required>
            <option value="">Role 선택</option>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
          <button type="button" onClick={quickCreateRole}>
            + Role
          </button>
        </div>

        <div className="formRow">
          <select
            value={form.permissionProfileId}
            onChange={(e) => setForm({ ...form, permissionProfileId: e.target.value })}
            required
          >
            <option value="">Permission Profile 선택</option>
            {permissionProfiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <button type="button" onClick={quickCreatePermissionProfile}>
            + Profile
          </button>
        </div>

        <select value={form.providerId} onChange={(e) => setForm({ ...form, providerId: e.target.value })} required>
          <option value="">AI Provider 선택</option>
          {providers.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.key})
            </option>
          ))}
        </select>

        <select value={form.workspaceId} onChange={(e) => setForm({ ...form, workspaceId: e.target.value })}>
          <option value="">Workspace 선택 (선택)</option>
          {workspaces.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>

        <input
          placeholder="Model (예: claude-sonnet-5, 선택)"
          value={form.model}
          onChange={(e) => setForm({ ...form, model: e.target.value })}
        />

        <button type="submit">Agent 생성</button>
      </form>

      {error && <p className="errorText">{error}</p>}

      <table className="table" cellPadding={8}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Role</th>
            <th>Provider</th>
            <th>Workspace</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.id}>
              <td>{a.name}</td>
              <td>{a.role.name}</td>
              <td>{a.provider.name}</td>
              <td>{a.workspace?.name ?? '-'}</td>
              <td>
                <button onClick={() => runAgent(a.id)}>Run</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: 타입체크**

Run: `cd apps/frontend && npx tsc -b --noEmit`
Expected: 에러 없음

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/src/pages/Agents.tsx apps/frontend/src/pages/Agents.module.css
git commit -m "feat(frontend): Agents 페이지 포팅"
```

---

## Task 17: ExecutionDetail 페이지 + 라우팅 최종 조립

**Files:**
- Create: `apps/frontend/src/pages/ExecutionDetail.tsx`
- Create: `apps/frontend/src/pages/ExecutionDetail.module.css`
- Modify: `apps/frontend/src/App.tsx` (Task 13의 placeholder를 최종 라우트로 교체)

**Interfaces:**
- Consumes: `api`, 모든 페이지 컴포넌트(Task 14~16), `react-router-dom`의 `useParams`, `Routes`/`Route`/`Link`/`Outlet`

- [ ] **Step 1: CSS 이식**

Task 13 Step 1에서 백업해둔 CSS를 가져온다:
```bash
cp apps/frontend-assets-tmp/ExecutionDetail.module.css apps/frontend/src/pages/ExecutionDetail.module.css
```

- [ ] **Step 2: ExecutionDetail.tsx 작성 (Next.js `params.id` prop → React Router `useParams`)**

`apps/frontend/src/pages/ExecutionDetail.tsx`:
```tsx
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../lib/api';
import styles from './ExecutionDetail.module.css';

interface LogLine {
  stream: 'stdout' | 'stderr';
  content: string;
}

export default function ExecutionDetail() {
  const { id } = useParams<{ id: string }>();
  const [status, setStatus] = useState<string>('PENDING');
  const [lines, setLines] = useState<LogLine[]>([]);
  const logRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    if (!id) return;
    const source = new EventSource(`${api.base}/executions/${id}/stream`);
    source.onmessage = (event) => {
      try {
        const log: LogLine = JSON.parse(event.data);
        setLines((prev) => [...prev, log]);
      } catch {
        // ignore malformed event
      }
    };
    source.onerror = () => {
      source.close();
    };

    const poll = setInterval(() => {
      api
        .getExecution(id)
        .then((exec) => {
          setStatus(exec.status);
          if (exec.status === 'SUCCEEDED' || exec.status === 'FAILED' || exec.status === 'CANCELLED') {
            clearInterval(poll);
          }
        })
        .catch(() => {});
    }, 1500);

    return () => {
      source.close();
      clearInterval(poll);
    };
  }, [id]);

  useEffect(() => {
    logRef.current?.scrollTo(0, logRef.current.scrollHeight);
  }, [lines]);

  return (
    <div>
      <h1>Execution {id}</h1>
      <p>
        Status: <strong>{status}</strong>
      </p>
      <pre ref={logRef} className={styles.log}>
        {lines.map((l, i) => (
          <div key={i} className={l.stream === 'stderr' ? styles.stderr : styles.stdout}>
            {l.content}
          </div>
        ))}
      </pre>
    </div>
  );
}
```

- [ ] **Step 3: App.tsx 최종본으로 교체**

`apps/frontend/src/App.tsx`:
```tsx
import { Route, Routes, Link, Outlet } from 'react-router-dom';
import styles from './layout.module.css';
import Dashboard from './pages/Dashboard';
import Providers from './pages/Providers';
import Workspaces from './pages/Workspaces/Workspaces';
import Agents from './pages/Agents';
import ExecutionDetail from './pages/ExecutionDetail';

function Layout() {
  return (
    <div>
      <nav className={styles.nav}>
        <Link to="/">Dashboard</Link>
        <Link to="/agents">Agents</Link>
        <Link to="/workspaces">Workspaces</Link>
        <Link to="/providers">AI Providers</Link>
      </nav>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/providers" element={<Providers />} />
        <Route path="/workspaces" element={<Workspaces />} />
        <Route path="/agents" element={<Agents />} />
        <Route path="/executions/:id" element={<ExecutionDetail />} />
      </Route>
    </Routes>
  );
}
```

- [ ] **Step 4: 타입체크 + 빌드**

Run: `cd apps/frontend && npx tsc -b --noEmit && npx vite build`
Expected: 에러 없음, `dist/` 생성

- [ ] **Step 5: CSS 백업용 임시 디렉터리 정리**

Task 13~17에서 쓰던 백업이 이제 전부 최종 위치로 옮겨졌으므로 정리한다:
```bash
rm -rf apps/frontend-assets-tmp
```

- [ ] **Step 6: 커밋**

```bash
git add apps/frontend/src/pages/ExecutionDetail.tsx apps/frontend/src/pages/ExecutionDetail.module.css apps/frontend/src/App.tsx
git commit -m "feat(frontend): ExecutionDetail 페이지 및 라우팅 최종 조립"
```

---

## Task 18: 프론트+백엔드 통합 E2E 검증, CONVENTIONS.md 갱신

**Files:**
- Modify: `agents/CONVENTIONS.md` (기술 스택 표, Backend/Frontend 모듈 절, 실행 방법 절 갱신)
- Modify: 루트 `package.json` (npm workspaces가 `apps/frontend`의 새 `package.json`을 그대로 인식하는지 확인, 스크립트명 `dev:backend`/`dev:frontend`가 Gradle/Vite 명령으로 바뀌었으므로 갱신)
- Modify: `.claude/launch.json` (frontend/backend 실행 명령이 npm 스크립트 이름 그대로 유지되는지 확인 — Task 13에서 `dev:test` 스크립트명을 그대로 유지했으므로 실제로는 변경 불필요할 가능성이 높다. 백엔드 쪽만 `npm run start:dev:test` 같은 NestJS 전용 스크립트명이 남아있다면 Gradle 실행으로 교체)

**Interfaces:**
- Consumes: 전체 태스크

- [ ] **Step 1: 루트 package.json 스크립트 갱신**

루트 `package.json`을 읽고 `dev:backend`/`dev:backend:test`가 여전히 `npm run ... --workspace=apps/backend`를 가리키는지 확인한다. `apps/backend`는 이제 npm workspace가 아니라 Gradle 프로젝트이므로, 루트 `package.json`에서 백엔드 관련 스크립트를 Gradle 호출로 바꾼다:

```json
{
  "scripts": {
    "dev:backend": "cd apps/backend && ./gradlew bootRun",
    "dev:backend:test": "cd apps/backend && PORT=8081 ./gradlew bootRun",
    "dev:frontend": "npm run dev --workspace=apps/frontend",
    "dev:frontend:test": "npm run dev:test --workspace=apps/frontend",
    "build:frontend": "npm run build --workspace=apps/frontend"
  },
  "workspaces": [
    "apps/frontend"
  ]
}
```
(`apps/backend`를 npm workspaces 배열에서 제거 — 더 이상 Node 프로젝트가 아니다)

- [ ] **Step 2: .claude/launch.json 확인/갱신**

현재 파일 내용을 확인하고, `backend` 항목의 `runtimeArgs`가 `npm run dev:backend:test` (또는 동등한 것)를 가리키는지 확인한다. Step 1에서 루트 스크립트명을 유지했다면 `.claude/launch.json`은 변경할 필요가 없다.

- [ ] **Step 3: agents/CONVENTIONS.md 갱신**

기술 스택 절:
```markdown
## 기술 스택

- Backend: Java 21 + Spring Boot 3 + Gradle(Kotlin DSL) + Spring Data JPA + QueryDSL + Flyway + PostgreSQL (`apps/backend`)
- Frontend: Vite + React 18 + React Router + TypeScript (`apps/frontend`)
- Local Runtime 실행: `ProcessBuilder`, 브라우저가 CLI를 직접 실행하지 않음
- 실시간 로그: SSE (Server-Sent Events, `SseEmitter`)
```

Backend 모듈 절의 각 항목을 Java 패키지 경로로 갱신(`apps/backend/src/main/java/com/agentdock/backend/...`), Prisma 관련 문구를 Flyway/JPA로 교체. DB 마이그레이션 절차를 다음으로 교체:
```markdown
## 실행 방법

Postgres 접속 정보는 OS 환경변수(`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`)로 설정한다.
Spring Boot는 `.env`를 자동으로 읽지 않으므로 `apps/backend/.env.example`을 참고해 직접 export 하거나
`application-local.yml`(gitignore 대상)을 만들어 override한다.

\`\`\`
# 1. 빈 데이터베이스 생성 (최초 1회)
createdb AGENT_DOCK

# 2. 백엔드 실행 — 기동 시 Flyway가 db/migration의 SQL을 자동 적용한다
npm run dev:backend    # apps/backend, PORT 8080 (Gradle bootRun)

# 3. 프론트 의존성 설치 + 실행
npm install
npm run dev:frontend   # apps/frontend, PORT 3030 (Vite)
\`\`\`

Flyway 마이그레이션을 추가할 때는 `apps/backend/src/main/resources/db/migration/V<N>__<설명>.sql`을
새 버전 번호로 만들어 추가한다(기존 파일을 수정하지 않는다).
```

- [ ] **Step 4: 통합 E2E — 테스트 포트로 백엔드+프론트 동시 기동**

```bash
cd apps/backend && PORT=8081 ./gradlew bootRun &
cd apps/frontend && VITE_API_BASE=http://localhost:8081 npm run dev:test &
```
브라우저(또는 `curl`)로 `http://localhost:3031`에 접속해:
1. Dashboard 렌더링 확인
2. Providers 페이지에서 Provider 등록 → 목록 갱신 확인
3. Workspaces 페이지에서 폴더 선택 모달 열고 하위 폴더 탐색 → 등록 확인
4. Agents 페이지에서 Role/Permission Profile 즉석 생성 → Agent 생성 → 목록에 표시 확인
5. Run 버튼 클릭 → `/executions/:id`로 이동 확인, 상태 표시 확인

Expected: 5단계 모두 에러 없이 동작(claude CLI가 없어 Execution이 FAILED로 끝나는 것은 무방 — 여기서 검증하는 것은 프론트-백엔드 연동과 라우팅).

- [ ] **Step 5: 정리**

```bash
psql -U postgres -d AGENT_DOCK -c 'TRUNCATE execution_log, execution, agent, workspace, permission_profile, agent_role, ai_connection, ai_provider RESTART IDENTITY CASCADE;'
```
두 `bootRun`/`vite` 프로세스를 종료한다. 8081/3031 포트가 비었는지 확인한다.

- [ ] **Step 6: 최종 커밋**

```bash
git add agents/CONVENTIONS.md package.json .claude/launch.json
git commit -m "docs: CONVENTIONS.md를 Java/Spring+React 스택 전환 내용으로 갱신"
```
