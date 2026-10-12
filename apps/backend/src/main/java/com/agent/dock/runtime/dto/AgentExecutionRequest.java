package com.agent.dock.runtime.dto;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

/**
 * Runtime 에 넘기는 한 스텝의 실행 요청.
 *
 * @param executionId   실행 id(로그 스트림 키)
 * @param prompt        이번 스텝의 사용자 프롬프트
 * @param workspacePath 이 실행이 도는 작업 디렉터리(cwd). 격리되면 루트가 만든 worktree 경로, 아니면 워크스페이스 경로다.
 * @param systemPrompt  프롬프트 계층(마스터 → 그룹 → 에이전트)을 합친 시스템 프롬프트
 * @param model         CLI 에 넘길 모델(null/빈 값이면 CLI 기본)
 * @param mode          CLI 권한 모드(null/빈 값이면 CLI 기본)
 * @param maxBudgetUsd  이 스텝에 쓸 수 있는 금액 상한(CLI --max-budget-usd, null 이면 제한 없음)
 * @param onLog         로그 한 줄 콜백(내용, 스트림 이름)
 * @param resumeSessionId 이어받을 CLI 세션 id. **같은 런타임일 때만** 의미가 있다(런타임이 다르면 세션도 다르다).
 *                        null/빈 값이면 새 세션으로 시작한다 — 같은 실행의 다음 스텝은 자기 세션을 이어받아
 *                        앞 스텝의 맥락을 다시 설명하지 않아도 되게 한다(입력 토큰 절약).
 * @param forkSession   세션을 이어받되 **분기**해 새 세션으로 갈라질지(Claude Code 의 --fork-session).
 *                      위임받은 자식은 부모 세션을 이어받되 자기 대화로 갈라지는 편이 안전하다.
 */
public record AgentExecutionRequest(
        String executionId,
        String prompt,
        String workspacePath,
        String systemPrompt,
        String model,
        String mode,
        BigDecimal maxBudgetUsd,
        BiConsumer<String, String> onLog,
        String resumeSessionId,
        boolean forkSession
) {
    /** 세션을 이어받지 않는 기본 실행(스텝 1, 또는 다른 런타임으로 넘어가는 경우). */
    public AgentExecutionRequest(String executionId, String prompt, String workspacePath, String systemPrompt,
                                 String model, String mode, BigDecimal maxBudgetUsd,
                                 BiConsumer<String, String> onLog) {
        this(executionId, prompt, workspacePath, systemPrompt, model, mode, maxBudgetUsd, onLog, null, false);
    }
}
