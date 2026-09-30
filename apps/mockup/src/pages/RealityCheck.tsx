import styles from './RealityCheck.module.css';

/** 실제 구현으로 넘어갈 때 막히는 지점. 목업 화면과 별개로, 여기서만 관리한다. */
interface Risk {
  id: number;
  title: string;
  level: '높음' | '중간';
  problem: string;
  now: string;
  actions: string[];
}

/** 위임이 실제로 일어난다면 거쳐야 하는 순서. */
const FLOW = [
  { title: '사용자', note: '하고 싶은 일을 말한다' },
  { title: '마스터 에이전트', note: '대화하며 계획을 세우고 분배를 결정' },
  { title: '그룹 리더 에이전트', note: '받아서 직접 처리할지 하위에 넘길지 판단' },
  { title: '하위 에이전트', note: '실제 작업 수행' },
];

const RISKS: Risk[] = [
  {
    id: 1,
    title: '실행 결과를 저장하지 않는다',
    level: '높음',
    problem: '위임하려면 "이 에이전트가 낸 산출물"을 다음 에이전트에게 넘겨야 하는데, 지금은 결과가 어디에도 남지 않는다.',
    now: '실행 결과는 exit code 만 돌려주고, 표준출력은 SSE 로그로 흘러가고 끝난다.',
    actions: [
      '실행 결과 텍스트를 Execution 에 저장한다(원문 또는 요약)',
      'Task 의 산출물(요약·파일 경로)로 연결한다',
      '다음 실행의 입력으로 넣는 규칙(얼마나 요약해 넘길지)을 정한다',
    ],
  },
  {
    id: 2,
    title: '실행이 일회성이다(세션이 없다)',
    level: '높음',
    problem: '한 번의 CLI 호출은 매번 새 세션이다. "마스터와 나눈 대화"를 이어가지 못하니, 같은 얘기를 반복해야 한다.',
    now: '런타임은 `-p <프롬프트> --output-format text` 로 한 번 실행하고 끝난다. 세션 재개를 쓰지 않는다.',
    actions: [
      'CLI 의 세션 재개 플래그를 `--help` 로 확인한다(추측 금지)',
      '대화 세션 id 를 저장해 마스터 대화를 이어붙인다',
      '재개가 실패하면 새 세션으로 폴백하고 그 사실을 화면에 남긴다',
    ],
  },
  {
    id: 3,
    title: '에이전트가 위임할 통로가 없다',
    level: '높음',
    problem: '마스터가 "이 일은 Backend Team 에" 라고 판단해도, 그 결정을 받아 실제 실행을 만드는 경로가 없다.',
    now: '프롬프트를 넘겨줄 뿐, 에이전트가 우리 백엔드를 호출할 도구가 없다.',
    actions: [
      'MCP 서버나 hooks 로 "작업 생성" 도구를 노출한다',
      '도구 호출을 검증해 하위 Execution 을 만든다',
      '하위 결과를 호출한 에이전트에게 되돌려 다시 판단하게 한다',
    ],
  },
  {
    id: 4,
    title: '무한 위임과 순환',
    level: '높음',
    problem: 'A 가 B 에, B 가 다시 A 에 맡기는 식으로 깊이·횟수 제한이 없으면 프로세스가 끝없이 늘어난다.',
    now: '목업은 항상 1단계(마스터 → 리더)만 모의하고 끝난다.',
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
    actions: [
      '프로젝트/전역 동시 실행 상한과 대기 큐를 둔다',
      '실행 타임아웃을 정한다',
      '상위를 취소하면 하위도 함께 취소한다(취소 전파)',
    ],
  },
  {
    id: 6,
    title: '같은 워크스페이스 동시 편집',
    level: '높음',
    problem: '여러 에이전트가 같은 폴더에서 동시에 파일을 고치면 서로 덮어써 작업이 깨진다. 실무에서 가장 먼저 부딪히는 벽이다.',
    now: '실행 디렉터리는 프로젝트의 기본 워크스페이스 하나뿐이다. 격리 개념이 없다.',
    actions: [
      '에이전트별로 git worktree(브랜치)를 만들어 격리한다',
      '병합 단계와 충돌 처리(누가 합칠지)를 정한다',
      '동시에 손대면 안 되는 작업은 직렬화한다',
    ],
  },
  {
    id: 7,
    title: 'LLM 라우팅의 비결정성',
    level: '중간',
    problem: '누구에게 맡길지 판단이 매번 달라지고, 정해둔 형식을 벗어난 답을 낼 수 있다.',
    now: '목업은 "리더가 받는다"는 고정 규칙으로 단순화해 두었다.',
    actions: [
      '도구/출력 스키마를 검증하고 실패하면 재시도한다',
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
    now: '목업에는 채팅 기록만 있다. 위임 트리·비용·실패 알림 화면이 없다.',
    actions: [
      '위험한 작업에는 사람 승인 단계를 둔다',
      '위임 트리와 실행별 입출력·비용을 보여주는 화면을 만든다',
      '실패를 모아 보여주고 재시도할 수 있게 한다',
    ],
  },
];

/** 실제 구현으로 갈 때 어디서 막히는지 한 화면에 모아 본다. */
export default function RealityCheck() {
  return (
    <div className={styles.page}>
      <h1>현실성 점검</h1>
      <p className={styles.lead}>
        목업에서는 채팅이 "모의 응답"으로 끝나지만, 실제로 마스터가 <strong>일을 나누고 그룹 리더가 받아 판단</strong>하게
        만들려면 아래를 실제로 구현해야 합니다. 지금 코드 기준으로 무엇이 비어 있는지 정리했습니다.
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
          폭주와 충돌을 막기 위해, ⑦ ⑧ ⑨ 는 안전하게 운영하기 위해 필요합니다.
        </p>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>막히는 지점 {RISKS.length}가지</h2>
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
