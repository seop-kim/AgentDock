package com.agent.dock.delegation.util;

import java.util.List;

/**
 * 위임 실행에 쓰는 프롬프트와 지시 파일. 계약 형식과 팀 로스터를 매번 함께 넣어 모델이 대상을 고르게 한다.
 *
 * <p><b>긴 내용은 프롬프트에 인라인하지 않고 파일로 넘긴다.</b> 자식 실행의 전체 지시(원래 요청·맡은 일·기대 결과·
 * 지난 결과·첨부 경로)는 {@link #childInstruction} 으로 마크다운 한 장을 만들어 작업 디렉터리
 * `&lt;cwd&gt;/.agentdock/prompts/&lt;실행 id&gt;.md` 에 쓰고, 프롬프트에는 {@link #instructionReference} 로 그 파일을
 * 가리키는 짧은 한 줄만 넣는다. 부모의 재판단 문맥("지난 단계 결과")도 {@link #stepContextReference} 로 파일을 가리킨다.
 * (파일 쓰기·경로 조립은 {@code DelegationService} 가 맡는다 — 여기는 순수 문자열만 만든다.)
 */
public final class DelegationPrompts {

    /** 지시·문맥 파일을 두는 폴더(작업 디렉터리 기준 상대 경로). cwd 밖으로 나가지 않는다. */
    public static final String PROMPT_FOLDER = ".agentdock/prompts";

    /** 자식 실행 프롬프트가 지시 파일임을 알리는 접두. */
    public static final String FILE_REFERENCE_PREFIX = "지시 파일: ";

    /** 지시 파일을 읽으라고 시키는 한 줄. */
    private static final String READ_INSTRUCTION = "이 파일을 먼저 읽고 그 내용대로 작업하세요.";

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

    // ── 지시·문맥 파일 (긴 내용을 프롬프트 밖으로 옮긴다) ───────────────────────────────

    /** 자식 실행의 지시 파일 이름(자식 실행 id). */
    public static String childPromptFileName(Long childExecutionId) {
        return childExecutionId + ".md";
    }

    /** 부모의 재판단 문맥 파일 이름(`&lt;실행 id&gt;-step&lt;N&gt;.md`). */
    public static String stepPromptFileName(Long executionId, int step) {
        return executionId + "-step" + step + ".md";
    }

    /** cwd 기준 상대 경로(예: `.agentdock/prompts/42.md`). */
    public static String promptFileReference(String fileName) {
        return PROMPT_FOLDER + "/" + fileName;
    }

    /**
     * 자식 실행의 "전체 지시"를 마크다운 한 장으로 만든다. 프롬프트에 인라인하던 원래 요청·맡은 일·기대 결과·
     * 지난 결과·첨부 파일(워크스페이스 기준 경로)을 여기로 모은다. 비어 있는 절(기대 결과·지난 결과·첨부)은 생략한다.
     */
    public static String childInstruction(String request, String order, String expects, String previous,
                                          List<String> attachments) {
        StringBuilder doc = new StringBuilder();
        doc.append("# 위임 지시\n\n");
        doc.append("## 원래 요청\n\n").append(block(request)).append("\n\n");
        doc.append("## 맡은 일\n\n").append(block(order)).append("\n");
        if (!isBlank(expects)) {
            doc.append("\n## 기대 결과\n\n").append(expects.strip()).append("\n");
        }
        if (!isBlank(previous)) {
            doc.append("\n## 지난 결과\n\n").append(previous.strip()).append("\n");
        }
        if (attachments != null && !attachments.isEmpty()) {
            doc.append("\n## 첨부 파일\n\n");
            attachments.forEach(path -> doc.append("- ").append(path).append("\n"));
        }
        doc.append("""

                ## 함께 일하기

                - 같은 명령으로 **다른 에이전트가 동시에** 돌고 있다(같은 작업 디렉터리를 쓴다).
                - 네가 알아낸 것 중 남이 바로 쓸 만한 사실은 `.agentdock/share/<네 이름>.md` 한 장으로 남겨라 —
                  형제와 다음 단계가 부모를 거치지 않고 곧바로 읽는다.
                - 시작할 때 `.agentdock/share/`(다른 에이전트의 공유)와 `.agentdock/prompts/`(지시·지난 결과)에
                  파일이 있으면 **먼저 읽어라**. 같은 팀의 맥락이 이미 파일로 와 있다.
                """);
        return doc.toString();
    }

    /**
     * 자식 실행의 실제 프롬프트: 지시 파일을 가리키는 짧은 한 줄(1~3줄). 기대 결과가 있으면 덧붙이고 없으면 생략한다.
     * 예: `지시 파일: .agentdock/prompts/42.md — 이 파일을 먼저 읽고 그 내용대로 작업하세요. 기대 결과: ...`
     */
    public static String instructionReference(String relativePath, String expects) {
        StringBuilder prompt = new StringBuilder()
                .append(FILE_REFERENCE_PREFIX).append(relativePath).append(" — ").append(READ_INSTRUCTION);
        if (!isBlank(expects)) {
            prompt.append(" 기대 결과: ").append(expects.strip());
        }
        return prompt.toString();
    }

    /** 지시 파일을 쓰지 못했을 때의 폴백: 지시를 프롬프트에 그대로 넣는다(예전 동작). */
    public static String inlineInstruction(String request, String order, String expects) {
        StringBuilder prompt = new StringBuilder()
                .append("[원래 요청]\n").append(block(request))
                .append("\n\n[맡은 일]\n").append(block(order));
        if (!isBlank(expects)) {
            prompt.append("\n\n[기대 결과]\n").append(expects.strip());
        }
        return prompt.toString();
    }

    /** 재판단 스텝에서 "지난 단계 결과"를 인라인하지 않고 파일로 가리키는 한 줄. */
    public static String stepContextReference(String relativePath) {
        return relativePath + " — 이 파일을 먼저 읽고 그 결과를 반영하세요.";
    }

    // ── 판단·작업 프롬프트 ─────────────────────────────────────────────────────────

    /**
     * 판단 프롬프트. `instruction` 은 이 실행이 처리할 지시(루트는 원래 요청, 위임받은 자식은 지시 파일 참조 한 줄)이고,
     * `progress` 는 "지난 단계 결과" 자리에 그대로 들어갈 문장(파일 참조 한 줄이나 폴백 인라인 문맥)이다.
     * 팀 로스터와 판단 규칙·스키마는 짧으므로 그대로 인라인한다.
     */
    public static String judgement(String projectName, String roster, String instruction, String progress, int maxTargets) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 프로젝트 \"").append(projectName).append("\" 의 리더입니다. 아래 팀을 보고 요청을 처리하세요.\n\n");
        prompt.append("[팀 목록]\n");
        prompt.append(roster.isBlank() ? "(팀 없음 — 직접 처리하세요)\n" : roster);
        prompt.append("\n[요청]\n").append(instruction).append("\n");
        if (!isBlank(progress)) {
            prompt.append("\n[지난 단계 결과]\n").append(progress);
        }
        prompt.append("""

                [판단 규칙]
                - 지난 단계 결과가 있으면 그것을 모아 마무리하세요(action=done + summary).
                - 요청이 특정 팀이나 팀원을 지목했으면 그대로 그 팀에 맡기세요(action=delegate). 지목은 사용자의 결정이므로 되돌리지 마세요.
                - 지목이 없고 일이 작거나 이미 끝났다면 직접 처리하세요(action=done + summary).
                - 팀원이 2명 이상이고 나눌 수 있는 일이면 한 사람에게 몰아주지 말고 나눠 맡기세요.
                - 자기 자신이나 나에게 일을 맡긴 상대(리더·부모)에게 되돌려 맡기지 마세요. 순환 위임은 거부되고
                  그 실행은 실패합니다. 맡길 곳이 없으면 직접 처리하거나(action=done) 사람에게 물으세요(action=ask).
                - targets 의 prompt(그 에이전트에게 줄 지시)를 **비워 두지 마세요**. 무슨 일을 맡기는지 한 줄이라도
                  적으세요. 맡길 일이 아직 정해지지 않았으면 위임하지 말고 직접 처리하거나 사람에게 물으세요.
                - 팀에 맡길 때는 targets 의 각 항목에 agentId(위 팀 목록의 id), prompt(그 에이전트에게 줄 지시),
                  expects(기대하는 결과)를 넣으세요. 한 번에 최대 %d건까지 맡길 수 있습니다.
                - 맡긴 일의 결과는 다음 단계에서 당신에게 돌아옵니다. 같은 일을 같은 사람에게 두 번 맡기지 마세요.
                  **이미 끝난 일을 다시 맡기지 마세요** — 같은 팀원에게 같은 지시를 반복하면 실행만 여러 개 생기고
                  결과는 나아지지 않습니다(한 명령에서 같은 에이전트에게 여러 번 재위임한 실측이 있습니다). 결과가
                  모자라면 직접 정리하거나(action=done) 사람에게 물으세요(action=ask).
                - 혼자 정할 수 없는 갈림길(범위·취향·되돌리기 어려운 변경)은 추측하지 말고 사람에게 물으세요
                  (action=ask + question, 필요하면 options). 답은 다음 단계에서 당신에게 돌아오므로 그 결정을 그대로 따르세요.
                  이미 답을 받은 것을 다시 묻지 마세요.
                - 출력은 아래 스키마를 정확히 따르는 JSON 하나만 내세요. 설명이나 코드블록 없이 JSON 만 출력하세요.
                %s
                """.formatted(maxTargets, ContractSchemas.JUDGEMENT));
        return prompt.toString();
    }

    /**
     * 작업(자식·직접처리) 실행의 프롬프트: 실행에 저장된 지시(루트는 원래 요청, 자식은 지시 파일 참조 한 줄)와
     * 작업 계약 스키마만 붙인다. 원래 요청·맡은 일은 이미 그 지시 안(또는 지시 파일 안)에 있다.
     */
    public static String work(String order) {
        return """
                %s

                일이 끝나면 아래 스키마를 정확히 따르는 JSON 하나만 내세요. 설명이나 코드블록 없이 JSON 만 출력하세요.
                %s
                """.formatted(order, ContractSchemas.WORK);
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

    private static String block(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private DelegationPrompts() {
    }
}
