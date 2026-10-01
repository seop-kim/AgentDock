package com.agent.dock.project;

/**
 * 프로젝트 마스터 지정/변경과 마스터 프롬프트. agentId 가 null 이면 마스터를 해제한다.
 */
public record UpdateMasterRequest(
        Long agentId,
        String masterPrompt
) {
}
