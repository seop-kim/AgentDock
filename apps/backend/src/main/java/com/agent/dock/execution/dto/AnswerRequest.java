package com.agent.dock.execution.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * `WAITING_INPUT` 인 실행(판단 실행이 계약 `ask` 로 사람에게 물은 것)에 넣는 답.
 * 이 답은 저장되고, 같은 실행의 다음 판단 스텝 프롬프트에 질문과 함께 붙는다.
 */
public record AnswerRequest(@NotBlank String text) {
}
