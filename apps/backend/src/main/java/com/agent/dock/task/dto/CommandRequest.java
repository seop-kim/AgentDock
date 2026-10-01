package com.agent.dock.task.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 프로젝트 채팅에서 보내는 명령. 대상을 주지 않으면 프로젝트 마스터가 받는다.
 * attachmentIds 는 채팅에 붙인 첨부(파일)이며 실행 프롬프트 끝에 경로가 덧붙는다.
 */
public record CommandRequest(
        @NotBlank String text,
        Long targetAgentId,
        Long groupId,
        List<Long> attachmentIds
) {
}
