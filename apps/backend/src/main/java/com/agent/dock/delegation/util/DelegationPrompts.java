package com.agent.dock.delegation.util;

import java.util.List;

/**
 * 위임 실행에 쓰는 프롬프트. 계약 형식과 팀 로스터를 매번 함께 넣어 모델이 대상을 고르게 한다.
 */
public final class DelegationPrompts {

    /**
     * 자식 실행 하나의 결과(부모의 다음 판단에 넣는다).
     *
     * @param waiting 사람의 답을 기다리는 중인지(`ask`). 그러면 그 자식은 끝나지 않았고 부모도 함께 멈춘다
     * @param note    기다리는 이유(질문 문장)나 실패 사유
     * @param options 자식이 사람에게 물은 질문의 보기(부모가 그대로 물려받아 화면에 다시 그린다). 없으면 빈 목록
     */
    public record ChildOutcome(String agentName, Long agentId, String statusLabel, String summary,
                              List<String> changedFiles, String note, List<String> options, boolean waiting) {

        /** 끝나거나 실패한 결과(사람의 입력을 기다리지 않는 보통의 경우). */
        public ChildOutcome(String agentName, Long agentId, String statusLabel, String summary,
                            List<String> changedFiles, String note) {
            this(agentName, agentId, statusLabel, summary, changedFiles, note, List.of(), false);
        }
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
                - 지난 단계 결과가 있으면 그것을 모아 마무리하세요(action=done + summary).
                - 요청이 특정 팀이나 팀원을 지목했으면 그대로 그 팀에 맡기세요(action=delegate). 지목은 사용자의 결정이므로 되돌리지 마세요.
                - 지목이 없고 일이 작거나 이미 끝났다면 직접 처리하세요(action=done + summary).
                - 팀원이 2명 이상이고 나눌 수 있는 일이면 한 사람에게 몰아주지 말고 나눠 맡기세요.
                - 팀에 맡길 때는 targets 의 각 항목에 agentId(위 팀 목록의 id), prompt(그 에이전트에게 줄 지시),
                  expects(기대하는 결과)를 넣으세요. 한 번에 최대 %d건까지 맡길 수 있습니다.
                - 맡긴 일의 결과는 다음 단계에서 당신에게 돌아옵니다. 같은 일을 같은 사람에게 두 번 맡기지 마세요.
                - 혼자 정할 수 없는 갈림길(범위·취향·되돌리기 어려운 변경)은 추측하지 말고 사람에게 물으세요
                  (action=ask + question, 필요하면 options). 답은 다음 단계에서 당신에게 돌아오므로 그 결정을 그대로 따르세요.
                  이미 답을 받은 것을 다시 묻지 마세요.
                - 출력은 아래 스키마를 정확히 따르는 JSON 하나만 내세요. 설명이나 코드블록 없이 JSON 만 출력하세요.
                %s
                """.formatted(maxTargets, ContractSchemas.JUDGEMENT));
        return prompt.toString();
    }

    public static String work(String request, String order) {
        return """
                [원래 요청]
                %s

                [당신이 맡은 일]
                %s

                일이 끝나면 아래 스키마를 정확히 따르는 JSON 하나만 내세요. 설명이나 코드블록 없이 JSON 만 출력하세요.
                %s
                """.formatted(request, order, ContractSchemas.WORK);
    }

    /** 계약을 못 읽었을 때 한 번 더 시도할 때 붙이는 문장. */
    public static String repair(String previousResult) {
        String previous = previousResult == null || previousResult.isBlank() ? "(출력 없음)" : previousResult;
        return """
                방금 출력에서 계약을 읽지 못했습니다.

                [방금 출력]
                %s

                [다시] 아래 스키마를 정확히 따르는 JSON 하나만 내세요. 설명이나 코드블록 없이 JSON 만 출력하세요.
                %s
                """.formatted(previous, ContractSchemas.JUDGEMENT);
    }

    /**
     * 사람의 답을 받아 이어서 도는 스텝의 "지난 단계 결과" 첫 줄. 계약 `ask` 로 물었던 질문과 사람의 답을
     * 그대로 넣어, 같은 것을 다시 묻지 않고 그 결정을 따르게 한다.
     */
    public static String answerContext(String question, String answer) {
        String asked = question == null || question.isBlank() ? "(질문 없음)" : question.strip();
        String given = answer == null || answer.isBlank() ? "(답 없음)" : answer.strip();
        return "- 사람에게 물은 질문: " + asked + "\n- 사람의 답: " + given + "\n";
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
                progress.append(" · ").append(outcome.waiting() ? "질문: " + outcome.note() : outcome.note());
            }
            progress.append("\n");
        }
        return progress.toString();
    }

    private DelegationPrompts() {
    }
}
