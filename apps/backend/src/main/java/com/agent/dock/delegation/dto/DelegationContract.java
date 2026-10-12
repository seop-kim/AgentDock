package com.agent.dock.delegation.dto;

import com.agent.dock.execution.domain.ExecutionDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 판단 실행(마스터·리더)이 출력 끝에 남기는 계약.
 *
 * <pre>
 * {"action":"delegate","targets":[{"agentId":12,"prompt":"…","expects":"…"}]}
 * {"action":"ask","question":"…","options":["…"]}
 * {"action":"done","summary":"…"}
 * {"action":"finish","summary":"…"}
 * </pre>
 *
 * 모델이 낸 문장을 해석하지 않는다. `--json-schema` 로 강제한 JSON 만 읽는다.
 */
public record DelegationContract(ExecutionDecision action, List<Order> orders, String summary,
                                 String question, List<String> options) {

    /** 한 에이전트에게 맡기는 한 건. */
    public record Order(Long agentId, String prompt, String expects) {
    }

    /** 위임 계약(규칙 라우터 폴백도 이 모양을 쓴다). */
    public static DelegationContract delegate(List<Order> orders, String summary) {
        return new DelegationContract(ExecutionDecision.DELEGATE, orders, summary, "", List.of());
    }

    /**
     * 자식 결과가 모두 성공해 **재판단 없이** 서버가 마무리할 때 쓰는 계약. `done` 과 같은 뜻이다 —
     * 마지막 계약은 DONE 으로 남고 요약만 자식 결과에서 모아 온다.
     */
    public static DelegationContract finish(String summary) {
        return new DelegationContract(ExecutionDecision.DONE, List.of(), summary, "", List.of());
    }

    /**
     * 자식이 **부모에게** 되묻는 계약. 사람(`ask`)과 달리 사람 알림을 띄우지 않는다 — 부모의 다음 판단 스텝이
     * 이 질문을 보고 그 자식에게 답을 주는 지시로 다시 맡긴다(실행당 1회로 제한한다).
     */
    public static DelegationContract askParent(String question, List<String> options) {
        return new DelegationContract(ExecutionDecision.ASK_PARENT, List.of(), "", question, options);
    }

    /** 스키마를 통과한 JSON 을 계약으로 읽는다. 형식이 어긋나면 비어 있다(재시도·폴백 대상). */
    public static Optional<DelegationContract> parse(Map<String, Object> payload) {
        if (payload == null) {
            return Optional.empty();
        }
        if (!(payload.get("action") instanceof String action)) {
            return Optional.empty();
        }
        String summary = text(payload.get("summary"));
        if ("done".equals(action) || "finish".equals(action)) {
            return Optional.of(new DelegationContract(ExecutionDecision.DONE, List.of(), summary, "", List.of()));
        }
        if ("ask".equals(action) || "askParent".equals(action)) {
            String question = text(payload.get("question"));
            if (question.isBlank()) {
                return Optional.empty();
            }
            List<String> options = options(payload.get("options"));
            if ("askParent".equals(action)) {
                return Optional.of(askParent(question, options));
            }
            return Optional.of(new DelegationContract(ExecutionDecision.ASK, List.of(), summary, question, options));
        }
        if (!"delegate".equals(action)) {
            return Optional.empty();
        }
        if (!(payload.get("targets") instanceof List<?> targets) || targets.isEmpty()) {
            return Optional.empty();
        }
        List<Order> orders = new ArrayList<>();
        for (Object item : targets) {
            if (!(item instanceof Map<?, ?> target)) {
                return Optional.empty();
            }
            Long agentId = number(target.get("agentId"));
            String prompt = text(target.get("prompt"));
            if (agentId == null || prompt.isBlank()) {
                return Optional.empty();
            }
            orders.add(new Order(agentId, prompt, text(target.get("expects"))));
        }
        return Optional.of(delegate(orders, summary));
    }

    /** `options`(선택 항목). 문자열이 아닌 항목은 버린다 — 없어도 되는 값이라 계약을 깨뜨리지 않는다. */
    private static List<String> options(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static String text(Object value) {
        return value instanceof String string ? string : "";
    }

    private static Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && string.matches("\\d+")) {
            return Long.parseLong(string);
        }
        return null;
    }
}
