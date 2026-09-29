package com.agent.dock.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai-connections")
@RequiredArgsConstructor
public class AiConnectionController {
    private final AiConnectionService service;

    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConnectionResponse check(@PathVariable Long id) {
        return service.check(id);
    }
}
