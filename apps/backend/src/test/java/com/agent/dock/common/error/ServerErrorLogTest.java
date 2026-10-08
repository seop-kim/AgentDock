package com.agent.dock.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 내부 오류 기록기. 화면의 확인 창이 이 값을 그대로 보여 주므로, 최근 순서와 상한·검색이 정확해야 한다.
 */
class ServerErrorLogTest {

    @Test
    void recordsNewestFirstWithMethodPathAndStack() {
        ServerErrorLog log = new ServerErrorLog();

        log.record("GET", "/projects", new IllegalStateException("boom"));
        ServerErrorLog.Entry newest = log.record("POST", "/commands", new RuntimeException("bang"));

        List<ServerErrorLog.Entry> recent = log.recent();
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).id()).isEqualTo(newest.id());
        assertThat(recent.get(0).method()).isEqualTo("POST");
        assertThat(recent.get(0).path()).isEqualTo("/commands");
        assertThat(recent.get(0).message()).isEqualTo("bang");
        assertThat(recent.get(0).stack()).contains("RuntimeException");
        assertThat(recent.get(1).path()).isEqualTo("/projects");
    }

    @Test
    void keepsOnlyTheMostRecentFifty() {
        ServerErrorLog log = new ServerErrorLog();
        for (int index = 0; index < 60; index++) {
            log.record("GET", "/x/" + index, new RuntimeException("e" + index));
        }

        List<ServerErrorLog.Entry> recent = log.recent();
        assertThat(recent).hasSize(50);
        assertThat(recent.get(0).path()).isEqualTo("/x/59");
        assertThat(recent.get(49).path()).isEqualTo("/x/10");
        assertThat(log.find(1)).isNull();
    }
}
