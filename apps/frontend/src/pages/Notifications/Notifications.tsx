import { useEffect, useState, type FormEvent } from 'react';
import { BellIcon, PlayIcon, PlusIcon, TrashIcon } from '../../components/icons';
import { featureNotice, useFeatureStore, type NotificationChannelKind } from '../../lib/featureStore';
import shared from '../../styles/shared.module.css';
import styles from './Notifications.module.css';

const KIND_LABEL: Record<NotificationChannelKind, string> = {
  WEBHOOK: '웹훅',
  SLACK: 'Slack',
  EMAIL: '메일',
};
const KINDS: NotificationChannelKind[] = ['WEBHOOK', 'SLACK', 'EMAIL'];

const TARGET_HINT: Record<NotificationChannelKind, string> = {
  WEBHOOK: 'https://example.com/hooks/agentdock',
  SLACK: '#team-alerts 또는 incoming webhook URL',
  EMAIL: 'ops@example.com (쉼표로 여러 명)',
};

const EVENTS: { value: string; label: string }[] = [
  { value: 'execution.completed', label: '실행 완료' },
  { value: 'execution.failed', label: '실행 실패' },
  { value: 'execution.waiting_input', label: '입력 대기(질문)' },
  { value: 'schedule.triggered', label: '예약 실행' },
];
const EVENT_LABEL: Record<string, string> = Object.fromEntries(EVENTS.map((event) => [event.value, event.label]));

/**
 * 알림(/notifications) — 웹훅·Slack·메일 채널과 규칙(어떤 이벤트에 보낼지)을 정한다.
 * 채널·규칙 CRUD·테스트 발송은 `GET/POST/PUT/DELETE /notifications/*` 에서 온다.
 * 자격증명은 CLI 로그인 세션 원칙과 어긋나지 않게 저장하지 않고, 받는 곳(주소)만 둔다.
 * API 가 아직 없으면 목업을 만들지 않고 미지원 문구를 남긴다.
 */
export default function Notifications() {
  const {
    notificationChannels,
    notificationRules,
    notificationStatus,
    loadNotifications,
    createChannel,
    deleteChannel,
    testChannel,
    createRule,
    deleteRule,
    setRuleEnabled,
  } = useFeatureStore();

  const [channelOpen, setChannelOpen] = useState(false);
  const [channelKind, setChannelKind] = useState<NotificationChannelKind>('WEBHOOK');
  const [channelName, setChannelName] = useState('');
  const [channelTarget, setChannelTarget] = useState('');

  const [ruleChannelId, setRuleChannelId] = useState('');
  const [ruleEvent, setRuleEvent] = useState(EVENTS[0].value);

  useEffect(() => {
    loadNotifications();
  }, []);

  const ready = notificationStatus === 'ready';
  const notice = featureNotice(notificationStatus);

  const channelNameOf = (id: number) =>
    notificationChannels.find((channel) => channel.id === id)?.name ?? `채널 #${id}`;
  const submitChannel = (event: FormEvent) => {
    event.preventDefault();
    if (channelName.trim() === '' || channelTarget.trim() === '') return;
    createChannel({ kind: channelKind, name: channelName.trim(), target: channelTarget.trim(), enabled: true });
    setChannelOpen(false);
    setChannelName('');
    setChannelTarget('');
  };

  const submitRule = (event: FormEvent) => {
    event.preventDefault();
    if (ruleChannelId === '') return;
    createRule({ channelId: Number(ruleChannelId), event: ruleEvent, enabled: true });
  };

  return (
    <div>
      <div className={styles.header}>
        <h1>
          <BellIcon size={20} /> 알림
        </h1>
        <button type="button" className={styles.primary} onClick={() => setChannelOpen(true)} disabled={!ready}>
          <PlusIcon size={16} /> 채널 추가
        </button>
      </div>
      <p className={shared.hint}>
        실행·예약이 끝나거나 실패했을 때, 사람의 답이 필요할 때 알림을 보낼 채널(웹훅·Slack·메일)과 규칙을 정합니다.
        자격증명은 여기에 저장하지 않고 받는 곳(주소)만 둡니다.
      </p>

      {notice !== null && <p className={shared.muted}>{notice}</p>}
      {channelOpen && (
        <form className={styles.form} onSubmit={submitChannel}>
          <div className={styles.grid}>
            <label className={styles.field}>
              <span>종류</span>
              <select
                value={channelKind}
                onChange={(event) => setChannelKind(event.target.value as NotificationChannelKind)}
              >
                {KINDS.map((kind) => (
                  <option key={kind} value={kind}>
                    {KIND_LABEL[kind]}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              <span>이름</span>
              <input
                value={channelName}
                onChange={(event) => setChannelName(event.target.value)}
                placeholder="백엔드 알림"
                required
              />
            </label>
          </div>
          <label className={styles.field}>
            <span>받는 곳</span>
            <input
              value={channelTarget}
              onChange={(event) => setChannelTarget(event.target.value)}
              placeholder={TARGET_HINT[channelKind]}
              required
            />
          </label>
          <div className={shared.actions}>
            <button type="submit" disabled={!ready || channelName.trim() === '' || channelTarget.trim() === ''}>
              채널 만들기
            </button>
            <button type="button" className={styles.ghost} onClick={() => setChannelOpen(false)}>
              취소
            </button>
          </div>
        </form>
      )}

      <h2>채널</h2>      {notificationChannels.length === 0 ? (
        <p className={shared.muted}>
          {ready ? '아직 채널이 없습니다. "채널 추가"로 만들어 보세요.' : '알림 채널 목록을 기다리는 중입니다.'}
        </p>
      ) : (
        <ul className={styles.list}>
          {notificationChannels.map((channel) => (
            <li key={channel.id} className={styles.row}>
              <div className={styles.rowMain}>
                <span className={styles.badge}>{KIND_LABEL[channel.kind]}</span>
                <span className={styles.rowName}>{channel.name}</span>
                <span className={`${styles.badge} ${channel.enabled ? styles.badgeOn : ''}`}>
                  {channel.enabled ? '켜짐' : '꺼짐'}
                </span>
                <span className={styles.target}>{channel.target}</span>
              </div>
              <div className={styles.rowActions}>
                <button
                  type="button"
                  className={styles.ghost}
                  onClick={() => testChannel(channel.id)}
                  disabled={!ready}
                >
                  <PlayIcon size={15} /> 테스트 발송
                </button>
                <button
                  type="button"
                  className={styles.danger}
                  onClick={() => deleteChannel(channel.id)}
                  disabled={!ready}
                >
                  <TrashIcon size={15} /> 삭제
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <h2>규칙</h2>      <form className={styles.ruleForm} onSubmit={submitRule}>
        <label className={styles.field}>
          <span>채널</span>
          <select value={ruleChannelId} onChange={(event) => setRuleChannelId(event.target.value)} disabled={!ready}>
            <option value="">채널 선택</option>
            {notificationChannels.map((channel) => (
              <option key={channel.id} value={channel.id}>
                {channel.name}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.field}>
          <span>이벤트</span>
          <select value={ruleEvent} onChange={(event) => setRuleEvent(event.target.value)} disabled={!ready}>
            {EVENTS.map((event) => (
              <option key={event.value} value={event.value}>
                {event.label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={!ready || ruleChannelId === ''}>
          규칙 추가
        </button>
      </form>

      {notificationRules.length === 0 ? (
        <p className={shared.muted}>아직 규칙이 없습니다.</p>
      ) : (
        <ul className={styles.list}>
          {notificationRules.map((rule) => (
            <li key={rule.id} className={styles.row}>
              <div className={styles.rowMain}>
                <span className={styles.rowName}>{channelNameOf(rule.channelId)}</span>
                <span className={styles.badge}>{EVENT_LABEL[rule.event] ?? rule.event}</span>
                <span className={`${styles.badge} ${rule.enabled ? styles.badgeOn : ''}`}>
                  {rule.enabled ? '켜짐' : '꺼짐'}
                </span>
              </div>
              <div className={styles.rowActions}>
                <button
                  type="button"
                  className={styles.ghost}
                  onClick={() => setRuleEnabled(rule.id, !rule.enabled)}
                  disabled={!ready}
                >
                  {rule.enabled ? '끄기' : '켜기'}
                </button>
                <button
                  type="button"
                  className={styles.danger}
                  onClick={() => deleteRule(rule.id)}
                  disabled={!ready}
                >
                  <TrashIcon size={15} /> 삭제
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}