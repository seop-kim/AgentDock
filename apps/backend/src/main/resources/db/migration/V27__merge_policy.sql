-- 병합 정책(전역 싱글턴). 트리 결과가 MERGED 로 끝나면 워크트리·브랜치를 자동 정리할지 정한다.
-- 한 행만 두도록 id SMALLINT + CHECK (id = 1) 로 강제한다(부분 유니크가 아니라 CHECK 가 맞다).
CREATE TABLE IF NOT EXISTS merge_policy (
    id                    SMALLINT PRIMARY KEY,
    auto_cleanup_enabled  BOOLEAN NOT NULL DEFAULT false,
    cleanup_delete_branch BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT merge_policy_singleton CHECK (id = 1)
);

-- 시드 한 행(항상 존재한다 — GET/PUT 이 방어 생성 없이도 한 행을 읽는다).
INSERT INTO merge_policy (id) VALUES (1) ON CONFLICT (id) DO NOTHING;