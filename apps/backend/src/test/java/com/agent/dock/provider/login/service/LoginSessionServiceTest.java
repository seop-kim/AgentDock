package com.agent.dock.provider.login.service;

import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.provider.login.domain.SessionKind;
import com.agent.dock.provider.login.dto.LoginEvent;
import com.agent.dock.provider.login.interfaces.LoginProcess;
import com.agent.dock.provider.repository.AiProviderRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
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
        List<List<String>> started;
        final List<String> written = new ArrayList<>();
        boolean closed;
        boolean failStart;

        @Override public void start(List<List<String>> commands) throws IOException {
            if (failStart) throw new IOException("no such file");
            started = commands;
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

    @Mock AiProviderRepository providerRepository;
    @Mock LoginProcessFactory processFactory;

    FakeLoginProcess process;
    TestClock clock;
    LoginSessionService service;

    private AiProvider providerOf(ProviderKey key) {
        AiProvider provider = new AiProvider();
        provider.setKey(key);
        return provider;
    }

    @BeforeEach
    void setUp() {
        process = new FakeLoginProcess();
        clock = new TestClock();
        service = new LoginSessionService(providerRepository,
                new LoginCommandRegistry(List.of(new ClaudeCodeLoginCommand())), processFactory, clock);
    }

    private void givenClaudeProvider() {
        when(providerRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(providerOf(ProviderKey.CLAUDE_CODE)));
        when(processFactory.create()).thenReturn(process);
    }

    @Test
    void startRunsFixedClaudeLoginCommand() {
        givenClaudeProvider();

        String sessionId = service.start(7L);

        assertThat(sessionId).isNotBlank();
        // 로그인 명령은 셸로 감싸 .cmd/.ps1 실행 파일도 쓸 수 있게 한다
        assertThat(process.started).hasSize(1);
        assertThat(process.started.get(0)).hasSize(3);
        assertThat(process.started.get(0).get(2)).contains("auth login");
    }

    @Test
    void lateSubscriberReplaysEarlierOutputThenReceivesLiveOutput() {
        givenClaudeProvider();
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
        givenClaudeProvider();
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
        givenClaudeProvider();

        String first = service.start(7L);
        String second = service.start(7L);

        assertThat(second).isEqualTo(first);
        verify(processFactory, times(1)).create();
    }

    @Test
    void inputIsWrittenWithNewline() {
        givenClaudeProvider();
        String sessionId = service.start(7L);

        service.input(sessionId, "abc123");

        assertThat(process.written).containsExactly("abc123\n");
    }

    @Test
    void inputAfterExitIsRejected() {
        givenClaudeProvider();
        String sessionId = service.start(7L);
        process.exit(0);

        assertThatThrownBy(() -> service.input(sessionId, "late")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void runtimeWithoutLoginCommandIsRejected() {
        when(providerRepository.findByIdAndDeletedAtIsNull(8L)).thenReturn(Optional.of(providerOf(ProviderKey.CODEX)));

        assertThatThrownBy(() -> service.start(8L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CODEX");
    }

    @Test
    void missingCliBinaryEndsSessionWithMessageAndExitMinusOne() {
        process.failStart = true;
        givenClaudeProvider();
        String sessionId = service.start(7L);

        List<LoginEvent> got = new ArrayList<>();
        service.subscribe(sessionId, got::add);

        assertThat(got).hasSize(2);
        assertThat(got.get(0).content()).contains("CLI 실행 실패");
        assertThat(got.get(1).exitEvent()).isTrue();
        assertThat(got.get(1).exitCode()).isEqualTo(-1);
    }

    @Test
    void idleSessionIsKilledAfterTimeout() {
        givenClaudeProvider();
        service.start(7L);

        clock.advance(Duration.ofMinutes(6));
        service.sweepIdle();

        assertThat(process.closed).isTrue();
    }

    @Test
    void activityResetsIdleTimer() {
        givenClaudeProvider();
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

    @Test
    void deletedRuntimeIsNotFound() {
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void installRunsStoredInstallSteps() {
        AiProvider provider = providerOf(ProviderKey.COMMAND_CODE);
        provider.setCapabilities(Map.of("install", List.of("npm install -g command-code")));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(provider));
        when(processFactory.create()).thenReturn(process);

        service.start(9L, SessionKind.INSTALL);

        assertThat(process.started).hasSize(1);
        assertThat(process.started.get(0)).contains("npm install -g command-code");
    }

    @Test
    void installAddsPrerequisiteStepsWhenRequiredToolIsMissing() {
        AiProvider provider = providerOf(ProviderKey.CODEX);
        provider.setCapabilities(Map.of(
                "install", List.of("npm install -g @openai/codex"),
                "installRequire", "definitely-missing-tool-xyz",
                "installPrerequisite", List.of("nvm install lts", "nvm use lts")));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(provider));
        when(processFactory.create()).thenReturn(process);

        service.start(9L, SessionKind.INSTALL);

        // 안내 1 + 선행 2 + 안내 1 + 본 설치 1
        assertThat(process.started).hasSize(5);
        assertThat(process.started.get(1)).contains("nvm install lts");
        assertThat(process.started.get(4)).contains("npm install -g @openai/codex");
    }

    @Test
    void installWithoutCommandIsRejected() {
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(providerOf(ProviderKey.COMMAND_CODE)));

        assertThatThrownBy(() -> service.start(9L, SessionKind.INSTALL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("설치 명령");
    }

    @Test
    void installAndLoginSessionsAreSeparate() {
        AiProvider provider = providerOf(ProviderKey.CLAUDE_CODE);
        provider.setCapabilities(Map.of("install", List.of("npm install -g @anthropic-ai/claude-code")));
        when(providerRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(provider));
        when(processFactory.create()).thenReturn(process);

        String login = service.start(7L, SessionKind.LOGIN);
        String install = service.start(7L, SessionKind.INSTALL);

        assertThat(install).isNotEqualTo(login);
        verify(processFactory, times(2)).create();
    }
}
