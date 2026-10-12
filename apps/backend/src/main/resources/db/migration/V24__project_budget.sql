-- 프로젝트 비용 예산 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 프로젝트마다 기간(일/주/월)별 예산을 하나 둔다(project_id UNIQUE). 그 기간의 실행 비용 합계가
-- 예산의 alert_ratio(기본 0.8)를 넘으면 경고를 한 번 보낸다 — alerted_period 에 그 기간 키를 남겨
-- 같은 기간에 두 번 보내지 않는다. 프로젝트를 지우면 함께 지운다(ON DELETE CASCADE).
CREATE TABLE IF NOT EXISTS project_budget (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES project(id) ON DELETE CASCADE,
    amount_usd NUMERIC(12,2) NOT NULL,
    period VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    alert_ratio NUMERIC(4,3) NOT NULL DEFAULT 0.8,
    alerted_period VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);