package com.agent.dock.event.service;

import com.agent.dock.event.dto.DataChangedEvent;
import com.agent.dock.event.dto.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 데이터가 바뀌었음을 전역 스트림({@link EventStreamService})에 알리는 얇은 도우미.
 *
 * <p>서비스 계층은 "무엇이 바뀌었는지"만 넘기고(아는 식별자만 싣는다 — 모르면 null),
 * 화면은 알림을 받아 **바뀐 조각만 REST 로 다시 읽는다**. 그래서 값싸야 한다 — DB 조회도, 직렬화도 알림에는 없다.
 * AOP 나 새 의존성 없이 이 컴포넌트 하나만 주입한다.
 */
@Component
@RequiredArgsConstructor
public class EventPublisher {
    private final EventStreamService streamService;

    /** 실행(트리 포함)의 상태·계약·결과·워크트리가 바뀌었다. */
    public void executionChanged(Long projectId, Long executionId, Long taskId) {
        publish(EventTypes.EXECUTION_CHANGED, projectId, executionId, taskId);
    }

    /** Task 가 만들어졌거나 상태가 바뀌었다. */
    public void taskChanged(Long projectId, Long taskId) {
        publish(EventTypes.TASK_CHANGED, projectId, null, taskId);
    }

    /** 에이전트가 만들어지거나 수정·삭제·재할당·배치됐다. */
    public void agentChanged(Long projectId) {
        publish(EventTypes.AGENT_CHANGED, projectId, null, null);
    }

    /** 그룹(팀)이나 그 멤버가 바뀌었다. */
    public void groupChanged(Long projectId) {
        publish(EventTypes.GROUP_CHANGED, projectId, null, null);
    }

    /** 프로젝트 자체(이름·설명·마스터·워크스페이스 할당)나 구성도 배치가 바뀌었다. */
    public void projectChanged(Long projectId) {
        publish(EventTypes.PROJECT_CHANGED, projectId, null, null);
    }

    /** 워크스페이스(폴더) 목록이 바뀌었다. */
    public void workspaceChanged() {
        publish(EventTypes.WORKSPACE_CHANGED, null, null, null);
    }

    /** AI 런타임(등록·on/off·삭제·모델/모드)이 바뀌었다. */
    public void runtimeChanged() {
        publish(EventTypes.RUNTIME_CHANGED, null, null, null);
    }

    /** 파일 첨부가 올라오거나 Task 에 연결됐다. */
    public void attachmentChanged(Long projectId, Long taskId) {
        publish(EventTypes.ATTACHMENT_CHANGED, projectId, null, taskId);
    }

    private void publish(String type, Long projectId, Long executionId, Long taskId) {
        streamService.publish(new DataChangedEvent(type, projectId, executionId, taskId));
    }
}
