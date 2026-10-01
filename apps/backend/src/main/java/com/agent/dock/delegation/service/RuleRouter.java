package com.agent.dock.delegation.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 규칙 먼저(Rule Router). 정해진 규칙으로 대상이 정해지면 그걸 쓰고, 아니면 마스터가 직접 판단한다.
 * 여기서는 요청 문장에 팀 이름이 걸리는지 본다(경로·확장자 규칙은 파일 첨부가 붙는 다음 단계에서).
 */
@Component
public class RuleRouter {

    /** 팀 이름에 흔히 들어가서 단독으로는 뜻이 없는 토큰. */
    private static final Set<String> STOP_WORDS = Set.of("팀", "team", "그룹", "group");

    public record Team(Long id, String name, Long leaderAgentId) {
    }

    /** 팀 이름 토큰이 요청에 나오면 그 팀 리더를 고른다. 많이·길게 걸린 팀이 이긴다. */
    public Optional<Long> route(String request, List<Team> teams) {
        if (request == null || request.isBlank()) {
            return Optional.empty();
        }
        String text = request.toLowerCase(Locale.ROOT);
        Long bestLeaderId = null;
        int bestLength = 0;
        int bestCount = 0;
        for (Team team : teams) {
            if (team.leaderAgentId() == null) {
                continue;
            }
            int length = 0;
            int count = 0;
            for (String token : tokens(team.name())) {
                if (text.contains(token)) {
                    length += token.length();
                    count++;
                }
            }
            if (count > 0 && (length > bestLength || (length == bestLength && count > bestCount))) {
                bestLeaderId = team.leaderAgentId();
                bestLength = length;
                bestCount = count;
            }
        }
        return Optional.ofNullable(bestLeaderId);
    }

    private List<String> tokens(String name) {
        return List.of(name.toLowerCase(Locale.ROOT).split("\\s+")).stream()
                .map(token -> token.replaceAll("[^\\p{L}\\p{N}]", ""))
                .filter(token -> token.length() >= 2 && !STOP_WORDS.contains(token))
                .toList();
    }
}
