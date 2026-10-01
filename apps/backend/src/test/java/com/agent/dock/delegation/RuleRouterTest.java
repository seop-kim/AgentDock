package com.agent.dock.delegation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRouterTest {
    private final RuleRouter router = new RuleRouter();

    private final List<RuleRouter.Team> teams = List.of(
            new RuleRouter.Team(1L, "백엔드 팀", 11L),
            new RuleRouter.Team(2L, "프론트엔드 팀", 21L),
            new RuleRouter.Team(3L, "리더 없는 팀", null));

    @Test
    void routesByTeamNameToken() {
        // 팀 이름이 문장에 나오면 그 팀 리더에게 맡긴다(문장 뜻은 마스터가 본다).
        assertThat(router.route("백엔드 팀에서 봐주세요. 재고 API 가 느립니다", teams)).contains(11L);
        assertThat(router.route("프론트엔드 화면이 깨져요", teams)).contains(21L);
    }

    @Test
    void returnsEmptyWhenNoTeamMatches() {
        assertThat(router.route("재고 API 응답이 느려요. N+1 의심됩니다", teams)).isEmpty();
        assertThat(router.route("안녕하세요", teams)).isEmpty();
        assertThat(router.route("", teams)).isEmpty();
        assertThat(router.route(null, teams)).isEmpty();
    }

    @Test
    void ignoresGenericTokensAndTeamsWithoutLeader() {
        // "팀" 만 걸리는 문장은 어느 팀도 고르지 않는다.
        assertThat(router.route("팀 하나 만들어줘", teams)).isEmpty();
        assertThat(router.route("리더 없는 팀 일 좀 해주세요", teams)).isEmpty();
    }

    @Test
    void teamMatchingMoreTokensWins() {
        List<RuleRouter.Team> overlapping = List.of(
                new RuleRouter.Team(1L, "백엔드 팀", 11L),
                new RuleRouter.Team(2L, "백엔드 결제 팀", 22L));

        // "백엔드" 하나만 걸린 팀보다 "백엔드"+"결제" 둘 다 걸린 팀이 이긴다.
        assertThat(router.route("백엔드 결제 정산이 이상해요", overlapping)).contains(22L);
        assertThat(router.route("백엔드 배포가 안 돼요", overlapping)).contains(11L);
    }
}
