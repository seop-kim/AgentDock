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
