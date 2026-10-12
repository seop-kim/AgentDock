import { useAgentDockStore } from '../store/AgentDockStore';
import styles from './AppStatus.module.css';

/**
 * 서버와의 **공통 상태**를 화면 위에 한 번만 띄운다 — 첫 로드, 요청 실패, 실시간 연결 끊김.
 * 화면마다 다른 로딩·비어 있음·오류는 각 화면이 그 화면의 데이터로 판단하고, 여기서는 스토어가 아는 전역 상태만 다룬다.
 * (`error` 는 어떤 요청이든 실패하면 채워지고, 다시 읽기에 성공하면 비워진다.)
 */
export default function AppStatus() {
  const { loading, error, streamConnected, reload } = useAgentDockStore();

  if (loading) {
    return (
      <div className={`${styles.status} ${styles.loading}`} role="status">
        서버에서 불러오는 중…
      </div>
    );
  }

  if (error !== null) {
    return (
      <div className={`${styles.status} ${styles.error}`} role="alert">
        <span className={styles.text} title={error}>
          {error}
        </span>
        <button type="button" className={styles.retry} onClick={() => void reload()}>
          다시 시도
        </button>
      </div>
    );
  }

  // 실시간 이벤트 스트림이 끊긴 동안에는 5초 안전망 폴링이 갱신을 대신한다(끊긴 사실을 숨기지 않는다).
  if (!streamConnected) {
    return (
      <div className={`${styles.status} ${styles.offline}`} role="status">
        실시간 연결이 끊겨 있습니다 — 다시 연결하는 동안 5초마다 다시 읽습니다.
      </div>
    );
  }

  return null;
}