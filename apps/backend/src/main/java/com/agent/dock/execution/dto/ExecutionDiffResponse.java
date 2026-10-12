package com.agent.dock.execution.dto;

import java.util.List;

/**
 * 실행 트리 루트가 바꾼 파일별 unified diff (GET /executions/{id}/diff).
 *
 * <p>git 은 요청 스레드가 아니라 workers 풀에서 셸 미경유(ProcessService)로 돌고, 결과는 CompletableFuture 로
 * 돌려준다. 트리 루트에 변경 파일 기록(changed_files)이나 커밋(result_commit)이 없으면 available=false 와
 * 사유를 싣는다 — 화면은 "나중에 다시 열거나 정리 전에 열어야 한다"는 안내를 그릴 수 있다.
 */
public record ExecutionDiffResponse(
        Long executionId,
        Long rootExecutionId,
        boolean available,
        String reason,
        List<FileDiff> files
) {
    /** 파일 하나의 diff. patch 가 null 이면 그 파일의 diff 를 만들지 못한 것이다(사유는 서버 로그에 남는다). */
    public record FileDiff(String status, String path, String patch, boolean truncated) {
    }

    public static ExecutionDiffResponse unavailable(Long executionId, Long rootExecutionId, String reason) {
        return new ExecutionDiffResponse(executionId, rootExecutionId, false, reason, List.of());
    }
}