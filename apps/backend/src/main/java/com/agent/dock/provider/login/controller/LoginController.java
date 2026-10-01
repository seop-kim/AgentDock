package com.agent.dock.provider.login.controller;

import com.agent.dock.provider.login.domain.SessionKind;
import com.agent.dock.provider.login.dto.LoginInputRequest;
import com.agent.dock.provider.login.dto.LoginSessionResponse;
import com.agent.dock.provider.login.service.LoginSessionService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 런타임 단위 CLI 명령 패널(로그인/설치). 로그인·설치 모두 폴더와 무관하게 런타임 전역이다. */
@RestController
@RequestMapping("/ai-providers")
@RequiredArgsConstructor
public class LoginController {
    private final LoginSessionService service;

    @PostMapping("/{providerId}/login")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginSessionResponse startLogin(@PathVariable Long providerId) {
        return new LoginSessionResponse(service.start(providerId, SessionKind.LOGIN));
    }

    /** capabilities.install 에 등록된 설치 명령을 실행한다. */
    @PostMapping("/{providerId}/install")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginSessionResponse startInstall(@PathVariable Long providerId) {
        return new LoginSessionResponse(service.start(providerId, SessionKind.INSTALL));
    }

    @GetMapping(value = "/command-sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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

    @PostMapping("/command-sessions/{sessionId}/input")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void input(@PathVariable String sessionId, @Valid @RequestBody LoginInputRequest request) {
        service.input(sessionId, request.text());
    }

    @DeleteMapping("/command-sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable String sessionId) {
        service.stop(sessionId);
    }
}
