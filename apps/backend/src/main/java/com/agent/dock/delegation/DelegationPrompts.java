package com.agent.dock.delegation;

import java.util.List;

/**
 * 위임 실행에 쓰는 프롬프트. 계약 형식과 팀 로스터를 매번 함께 넣어 모델이 대상을 고르게 한다.
 */
public final class DelegationPrompts {

    /** 자식 실행 하나의 결과(부모의 다음 판단에 넣는다). */
    public record ChildOutcome(String agentName, Long agentId, String statusLabel, String summary,
                               List<String> changedFiles, String note) {
    }

    public static String judgement(String projectName, String roster, String request, String progress, int maxTargets) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 프로젝트 \"").append(projectName).append("\" 의 리더입니다. 아래 팀을 보고 요청을 처리하세요.\n\n");
        prompt.append("[팀 목록]\n");
        prompt.append(roster.isBlank() ? "(팀 없음 — 직접 처리하세요)\n" : roster);
        prompt.append("\n[요청]\n").append(request).append("\n");
        if (!progress.isBlank()) {
            prompt.append("\n[지난 단계 결과]\n").append(progress);
        }
        prompt.append("""

                [판단 규칙]
                - 직접 처리하는 편이 낫거나 일이 끝났다면 action=done 과 summary 를 남기세요.
                - 팀에 맡기는 편이 낫다면 action=delegate 와 targets 를 남기세요.
                  targets 의 각 항목은 agentId(위 팀 목록의 id), prompt(그 에이전트에게 줄 지시), expects(기대하는 결과) 입니다.
                - 한 번에 최대 %d건까지 맡길 수 있습니다. 같은 에이전트에게 같은 일을 두 번 맡기지 마세요.
                - 맡긴 일의 결과는 다음 단계에서 당신에게 돌아옵니다. 지난 단계 결과를 보고 마무리하세요.
                - 출력은 지정된 스키마의 JSON 하나만 내세요.
                """.formatted(maxTargets));
        return prompt.toString();
    }

    public static String work(String request, String order) {
        return """
                [원래 요청]
                %s

                [당신이 맡은 일]
                %s

                일이 끝나면 summary(무엇을 했는지 한두 문장)와 changedFiles(바꾼 파일 경로)를 남기세요.
                """.formatted(request, order);
    }

    /** 계약을 못 읽었을 때 한 번 더 시도할 때 붙이는 문장. */
    public static String repair(String previousResult) {
        String previous = previousResult == null || previousResult.isBlank() ? "(출력 없음)" : previousResult;
        return """
                방금 출력에서 계약을 읽지 못했습니다.

                [방금 출력]
                %s

                지정된 스키마의 JSON 하나만 다시 내세요. 설명이나 코드 블록 없이 JSON 만 출력하세요.
                """.formatted(previous);
    }

    public static String childResults(List<ChildOutcome> outcomes) {
        StringBuilder progress = new StringBuilder();
        for (ChildOutcome outcome : outcomes) {
            progress.append("- ").append(outcome.agentName());
            if (outcome.agentId() != null) {
                progress.append("(id=").append(outcome.agentId()).append(")");
            }
            progress.append(" [").append(outcome.statusLabel()).append("]: ")
                    .append(outcome.summary().isBlank() ? "(요약 없음)" : outcome.summary());
            if (!outcome.changedFiles().isEmpty()) {
                progress.append(" · 변경 파일: ").append(String.join(", ", outcome.changedFiles()));
            }
            if (!outcome.note().isBlank()) {
                progress.append(" · ").append(outcome.note());
            }
            progress.append("\n");
        }
        return progress.toString();
    }

    private DelegationPrompts() {
    }
}
