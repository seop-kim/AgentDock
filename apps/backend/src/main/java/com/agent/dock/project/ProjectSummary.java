package com.agent.dock.project;

/** 다른 응답에 끼워 넣는 가벼운 프로젝트 정보(에이전트 응답 등). */
public record ProjectSummary(Long id, String name) {
    public static ProjectSummary from(Project p) {
        return new ProjectSummary(p.getId(), p.getName());
    }
}
