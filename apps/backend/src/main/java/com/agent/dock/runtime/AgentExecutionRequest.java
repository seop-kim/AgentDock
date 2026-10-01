package com.agent.dock.runtime;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

/**
 * Runtime 에 넘기는 한 스텝의 실행 요청.
 *
 * @param executionId     실행 id(로그 스트림 키)
 * @param prompt          이번 스텝의 사용자 프롬프트
 * @param workspacePath   작업 디렉터리(프로젝트 기본 워크스페이스)
 * @param systemPrompt    프롬프트 계층(마스터 → 그룹 → 에이전트)을 합친 시스템 프롬프트
 * @param model           CLI 에 넘길 모델(null/빈 값이면 CLI 기본)
 * @param mode            CLI 권한 모드(null/빈 값이면 CLI 기본)
 * @param contractSchema  판단 실행이면 계약 JSON 스키마(작업 실행이면 null)
 * @param maxBudgetUsd    이 스텝에 쓸 수 있는 금액 상한(CLI --max-budget-usd, null 이면 제한 없음)
 * @param onLog           로그 한 줄 콜백(내용, 스트림 이름)
 */
public record AgentExecutionRequest(
        String executionId,
        String prompt,
        String workspacePath,
        String systemPrompt,
        String model,
        String mode,
        String contractSchema,
        BigDecimal maxBudgetUsd,
        BiConsumer<String, String> onLog
) {
}
