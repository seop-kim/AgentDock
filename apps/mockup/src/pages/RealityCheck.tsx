import styles from './RealityCheck.module.css';

/** 실제 구현으로 넘어갈 때 막히는 지점. 목업 화면과 별개로, 여기서만 관리한다. */
interface Risk {
  id: number;
  title: string;
  level: '높음' | '중간';
  problem: string;
  now: string;
  /** 확정한 해결 방안(여러 안 중 고른 것). */
  decision: string;
  actions: string[];
}

/** 위임이 실제로 일어난다면 거쳐야 하는 순서. 리더 계층은 항상 거치는 경로가 아니다. */
const FLOW = [
  { title: '사용자', note: '하고 싶은 일을 말한다' },
  { title: '마스터 에이전트', note: '계획을 세우고 직접 처리할지 위임할지 정한다(항상 거친다)' },
  { title: '그룹 리더 에이전트', note: '위임했을 때만 거친다. 받아서 직접 처리할지 하위에 넘길지 판단' },
  { title: '하위 에이전트', note: '실제 작업 수행' },
];

/**
 * 실제 CLI(`claude --help` / 최소 1회 실행)로 확인한 사실. 아래 결정들은 여기에 기대고 있다.
 * 확인 없이 세운 계획은 전제가 틀어지면 통째로 무너지므로, 근거를 화면에 함께 둔다.
 */
const VERIFIED: { title: string; detail: string }[] = [
  {
    title: '세션 재개가 있다',
    detail: '`--resume` / `--continue` / `--session-id` / `--fork-session` (claude --help 로 확인)',
  },
  {
    title: '출력 형식을 강제할 수 있다',
    detail: '`--output-format json | stream-json`, `--json-schema <schema>` — 계약을 프롬프트로 부탁하지 않아도 된다',
  },
  {
    title: '계측값이 그대로 온다',
    detail:
      '`--output-format json` → usage{input, output, cache_read, cache_creation} · total_cost_usd · duration_ms · session_id · num_turns · permission_denials',
  },
  {
    title: '한 번의 호출 고정비가 크다',
    detail: '"OK" 한 마디 실측: 입력 43,830(대부분 캐시 생성) · 출력 4 · $0.263 · 3.7초 → 위임은 필요할 때만',
  },
  {
    title: 'CLI 안에 서브에이전트가 있다',
    detail: '`--agents <json>`, `--agent`, `--forward-subagent-text`, `claude agents` — 우리 오케스트레이션과 비교할 대조군',
  },
];

const RISKS: Risk[] = [
  {
    id: 1,
    title: '실행 결과를 저장하지 않는다',
    level: '높음',
    problem: '위임하려면 "이 에이전트가 낸 산출물"을 다음 에이전트에게 넘겨야 하는데, 지금은 결과가 어디에도 남지 않는다.',
    now: '실행 결과는 exit code 만 돌려주고, 표준출력은 SSE 로그로 흘러가고 끝난다.',
    decision:
      '`--output-format json` 이 최종 텍스트(result)와 계측값(usage · total_cost_usd · duration_ms · session_id)을 함께 돌려주므로 그대로 저장한다. 위임할 때 넘기는 것은 Handoff(상태 · 요약 · 변경 파일 · 확인한 것 · 테스트 · 남은 문제)로 제한한다 — stdout 원문이나 코드 전체를 넘기면 컨텍스트가 폭발한다.',
    actions: [
      'result · usage · 비용 · 시간 · 세션 id 를 Execution 에 저장한다',
      'Handoff 구조(status · summary · changedFiles · findings · tests · risks · unresolvedIssues)를 정의한다',
      '원문은 로그에서 조회하고, 위임에는 요약만 넘긴다',
    ],
  },
  {
    id: 2,
    title: '실행이 일회성이다(세션이 없다)',
    level: '높음',
    problem: '한 번의 CLI 호출은 매번 새 세션이다. "마스터와 나눈 대화"를 이어가지 못하니, 같은 얘기를 반복해야 한다.',
    now: '런타임은 `-p <프롬프트> --output-format text` 로 한 번 실행하고 끝난다. 세션 재개를 쓰지 않는다.',
    decision:
      '세션 재개는 있다(`--resume` / `--continue` / `--session-id` / `--fork-session` — CLI 로 확인함). 세션이 필요한 곳은 마스터 대화 하나뿐이므로 `--session-id` 로 세션을 고정하고 `--resume` 으로 잇는다. 위임 실행은 일회성으로 둔다. 같은 세션을 두 실행이 동시에 쓰는 일은 피한다(대화별로 세션을 분리).',
    actions: [
      '마스터 실행에 `--session-id` 를 주고 다음 호출은 `--resume` 으로 잇는다',
      '대화별로 세션을 분리해 다른 대화와 섞이지 않게 한다',
      '재개가 실패하면 새 세션으로 폴백하고 그 사실을 화면에 남긴다',
    ],
  },
  {
    id: 3,
    title: '에이전트가 위임할 통로가 없다',
    level: '높음',
    problem: '마스터가 "이 일은 Backend Team 에" 라고 판단해도, 그 결정을 받아 실제 실행을 만드는 경로가 없다.',
    now: '프롬프트를 넘겨줄 뿐, 에이전트가 우리 백엔드를 호출할 통로가 없다.',
    decision:
      'MCP 서버를 두지 않는다. CLI 에 `--json-schema` 가 있으므로 실행 출력의 계약(action: delegate | done)을 스키마로 강제하고, 백엔드가 그것을 파싱해 하위 CLI 를 실행한 뒤 결과 요약을 붙여 같은 에이전트를 다시 호출한다(스텝당 프로세스 1개). 순수 백엔드 오케스트레이션이다.',
    actions: [
      '실행 프롬프트에 출력 계약을 넣고 `--json-schema` 로 형식을 강제한다',
      'stdout 의 JSON 을 파싱해 하위 실행을 만든다(검증 실패 시 1회 재시도)',
      'delegate 면 하위 CLI 를 실행하고, Handoff 를 붙여 같은 에이전트를 다시 호출한다(깊이·예산 상한 적용)',
    ],
  },
  {
    id: 4,
    title: '무한 위임과 순환',
    level: '높음',
    problem: 'A 가 B 에, B 가 다시 A 에 맡기는 식으로 깊이·횟수 제한이 없으면 프로세스가 끝없이 늘어난다.',
    now: '목업은 항상 1단계(마스터 → 리더)만 모의하고 끝난다.',
    decision:
      '깊이 3, 루트당 실행 20건, 예산 상한으로 자른다. 초과한 위임은 거부하고 사유를 위로 돌려보내 에이전트가 사용자에게 보고하게 한다.',
    actions: [
      '위임 깊이·총 횟수·비용 상한을 둔다',
      '순환(A→B→A)을 감지해 차단한다',
      '부모-자식 관계를 저장해 화면에서 볼 수 있게 한다',
    ],
  },
  {
    id: 5,
    title: '동시 실행과 비용',
    level: '중간',
    problem: '위임 1건은 프로세스 1개다. 상한이 없으면 동시에 수십 개가 뜨고 토큰이 순식간에 사라진다.',
    now: '실행 단위로 프로세스를 띄우고 취소만 지원한다. 전역 큐나 동시 실행 상한은 없다.',
    decision:
      '전역 동시 4 / 프로젝트 2 + FIFO 큐. 대기 중 실행은 QUEUED 로 표시한다. 타임아웃은 두 종류로 둔다 — Idle 10분(출력이 멈추면) / Hard 30~60분(전체 상한). 취소는 실행 트리 전파만으로 부족하다: AI CLI 가 shell → npm → node 를 낳으므로 OS 프로세스 트리까지 종료해야 한다(Windows 는 taskkill /T /F).',
    actions: [
      '프로젝트/전역 동시 실행 상한과 대기 큐를 둔다',
      'Idle / Hard 타임아웃을 따로 두고 실제 데이터로 조정한다',
      '취소는 실행 트리 전파 + 프로세스 트리 종료로 처리한다',
      '첫 버전은 직렬 실행으로 시작해 충돌 자체를 피한다',
    ],
  },
  {
    id: 6,
    title: '같은 워크스페이스 동시 편집',
    level: '높음',
    problem: '여러 에이전트가 같은 폴더에서 동시에 파일을 고치면 서로 덮어써 작업이 깨진다. 실무에서 가장 먼저 부딪히는 벽이다.',
    now: '실행 디렉터리는 프로젝트의 기본 워크스페이스 하나뿐이다. 격리 개념이 없다.',
    decision:
      '편집하는 실행은 git worktree(전용 브랜치)로 격리하고, 읽기만 하는 실행은 원본 폴더에서 돌린다. 자동 병합은 하지 않는다 — 결과는 ACCEPT / REJECT / NEEDS_REWORK 로 판정하고, 판정 전에는 worktree 를 지우지 않는다. 파일 밖의 공유 자원(포트·DB)은 worktree 로 막히지 않으므로 별도 규칙이 필요하다.',
    actions: [
      '편집 실행은 worktree + 전용 브랜치로 격리한다',
      'worktree 경로 · 브랜치 · base/head 커밋을 실행에 기록한다',
      '결과 판정(ACCEPT/REJECT/NEEDS_REWORK)과 병합 주체를 정한다',
      '같은 포트나 DB 를 쓰는 실행은 직렬화한다',
    ],
  },
  {
    id: 7,
    title: 'LLM 라우팅의 비결정성',
    level: '중간',
    problem: '누구에게 맡길지 판단이 매번 달라지고, 정해둔 형식을 벗어난 답을 낼 수 있다.',
    now: '목업은 "리더가 받는다"는 고정 규칙으로 단순화해 두었다.',
    decision:
      '규칙 먼저(Rule Router): 파일 확장자·경로·키워드로 팀이 정해지면 LLM 을 부르지 않는다. 규칙으로 못 정할 때만 마스터에게 맡기고, 그 출력은 `--json-schema` 로 형식을 강제한다. 그래도 검증에 실패하면 1회 재시도 후 사람에게 넘긴다.',
    actions: [
      '규칙으로 정할 수 있는 라우팅을 먼저 처리한다(경로·키워드)',
      '마스터 출력은 `--json-schema` 로 강제하고, 실패하면 1회 재시도한다',
      '재시도로도 안 되면 사람에게 넘긴다(에스컬레이션)',
      '판단 근거를 로그로 남겨 나중에 검토할 수 있게 한다',
    ],
  },
  {
    id: 8,
    title: '권한 상속과 축소',
    level: '중간',
    problem: '하위 에이전트가 상위보다 많은 권한을 가지면 안 된다. 프롬프트로만 막으면 우회된다.',
    now: '권한은 백엔드에서 403 으로 강제하는 기반이 이미 있다.',
    decision:
      '위임할 때 유효 권한 = 상위 ∩ 하위 를 계산해 자식 실행에 고정하고, 자식은 그 권한으로만 실행한다. 초과 시도는 거부하고 감사 로그로 남긴다.',
    actions: [
      '위임할 때 유효 권한을 상위 ∩ 하위로 계산한다',
      '위임 도구 호출에도 같은 가드를 건다',
      '권한이 막힌 시도를 감사 로그로 남긴다',
    ],
  },
  {
    id: 9,
    title: '승인과 관측',
    level: '중간',
    problem: '배포·삭제 같은 위험한 작업이 승인 없이 돌거나, 누가 누구에게 무엇을 맡겼는지 알 수 없으면 운영할 수 없다.',
    now: '목업에 실행 트리와 실행별 지표(모의)를 붙여 두었다. 실패·재시도 알림은 아직 없다.',
    decision:
      '위험 작업은 승인 대기로 멈추고 사람이 승인한다. 실행 트리(부모-자식)와 실행별 입출력·상태·시간·비용은 목업과 같은 모양으로 보여준다.',
    actions: [
      '위험한 작업에는 사람 승인 단계를 둔다',
      '실패를 모아 보여주고 재시도할 수 있게 한다',
      '위임 거절·재위임 이력과 감사 로그를 남긴다',
    ],
  },
];

/** 실제 구현으로 갈 때 어디서 막히는지 한 화면에 모아 본다. */
export default function RealityCheck() {
  return (
    <div className={styles.page}>
      <h1>현실성 점검</h1>
      <p className={styles.lead}>
        목업에서 마스터에게 명령을 보내면 <strong>실행 트리</strong>가 실제 순서(판단 → 위임 → 하위 실행 → 취합)대로 돌고,
        실행별 상태·토큰·비용·시간이 남습니다(값은 모의). 이걸 실제 제품으로 만들려면 아래를 구현해야 하고, 항목마다{' '}
        <strong>결정</strong>은 여러 안 중 고른 것입니다.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>위임이 실제로 일어난다면</h2>
        <div className={styles.flow}>
          {FLOW.map((hop) => (
            <div className={styles.hop} key={hop.title}>
              <strong className={styles.hopTitle}>{hop.title}</strong>
              <span className={styles.hopNote}>{hop.note}</span>
            </div>
          ))}
        </div>
        <p className={styles.flowNote}>
          화살표마다 아래 위험 항목이 걸립니다. ① ② 는 대화를 이어가기 위해, ③ 은 분배를 실제 실행으로 옮기기 위해, ④ ⑤ ⑥ 은
          폭주와 충돌을 막기 위해, ⑦ ⑧ ⑨ 는 안전하게 운영하기 위해 필요합니다. <strong>리더 계층은 필수 경로가 아닙니다</strong>
          — 마스터가 직접 끝내거나 하위 에이전트를 바로 지목할 수 있어서, 실제 실행 경로는 조직도와 달라질 수 있습니다.
        </p>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>확인된 사실</h2>
        <ul className={styles.verified}>
          {VERIFIED.map((item) => (
            <li key={item.title}>
              <strong>{item.title}</strong>
              <span>{item.detail}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>막히는 지점과 해결 방안 {RISKS.length}가지</h2>
        <div className={styles.cards}>
          {RISKS.map((risk) => (
            <article className={styles.card} key={risk.id}>
              <div className={styles.cardHead}>
                <span className={styles.index}>{risk.id}</span>
                <h3 className={styles.cardTitle}>{risk.title}</h3>
                <span
                  className={`${styles.level} ${risk.level === '높음' ? styles.levelHigh : styles.levelMedium}`}
                  title={`위험도 ${risk.level}`}
                >
                  위험도 {risk.level}
                </span>
              </div>
              <p className={styles.problem}>{risk.problem}</p>
              <div className={styles.decision}>
                <span className={styles.decisionLabel}>결정</span>
                {risk.decision}
              </div>
              <dl className={styles.rows}>
                <dt>지금</dt>
                <dd>{risk.now}</dd>
                <dt>할 일</dt>
                <dd>
                  <ul className={styles.actions}>
                    {risk.actions.map((action) => (
                      <li key={action}>{action}</li>
                    ))}
                  </ul>
                </dd>
              </dl>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
