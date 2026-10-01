package com.agent.dock.agent.repository;

import com.agent.dock.agent.domain.Agent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long>, AgentRepositoryCustom {

    /** 프로젝트의 에이전트 전부(구성도 위치 초기화·프로젝트 삭제에 쓴다). */
    List<Agent> findByProjectId(Long projectId);
}
