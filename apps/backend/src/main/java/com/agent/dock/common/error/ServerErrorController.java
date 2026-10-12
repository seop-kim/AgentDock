package com.agent.dock.common.error;

import com.agent.dock.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 내부 오류를 화면에서 볼 수 있게 내려 준다.
 *
 * <p>화면은 이걸 읽어 "사용자 확인 창"에 원인을 보여 주고, 사람이 **에이전트에게 수정을 맡길지** 고른다.
 * 실제 위임은 새 API 가 아니라 **기존 명령 경로**(`POST /projects/{id}/commands`)를 그대로 쓴다 —
 * 그러면 워크트리 격리·검증·커밋·병합까지 기존 흐름이 다 적용된다.
 */
@RestController
@RequestMapping("/errors")
@RequiredArgsConstructor
public class ServerErrorController {

    private final ServerErrorLog errors;

    /** 최근 오류(새 것이 먼저). 화면의 확인 창이 이걸 읽는다. */
    @GetMapping("/recent")
    public List<ServerErrorLog.Entry> recent() {
        return errors.recent();
    }

    @GetMapping("/{id}")
    public ServerErrorLog.Entry one(@PathVariable long id) {
        ServerErrorLog.Entry entry = errors.find(id);
        if (entry == null) {
            throw new NotFoundException("Error %d not found".formatted(id));
        }
        return entry;
    }
}
