package com.agent.dock.execution.domain;

/**
 * 트리가 바꾼 파일 하나. git 의 `--name-status` 한 줄에 대응한다.
 *
 * @param status git 변경 상태 코드(A 추가 / M 수정 / D 삭제 / R 이름 바꾸기 등)
 * @param path   파일 경로(저장소 루트 기준 상대 경로)
 */
public record ChangedFile(String status, String path) {
}
