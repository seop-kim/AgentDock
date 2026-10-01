package com.agent.dock.task.dto;

import com.agent.dock.task.domain.Task;

/** 명령 하나 = Task 하나 + 실행 트리 하나. 화면은 taskId 로 기록을, rootExecutionId 로 트리를 본다. */
public record CommandResponse(Long taskId, Long rootExecutionId) {
}
