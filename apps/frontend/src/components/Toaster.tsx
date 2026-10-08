import { useEffect, useState } from 'react';
import styles from './Toaster.module.css';

/** 알림 종류(색). */
export type ToastTone = 'info' | 'success' | 'error';

interface ToastItem {
  id: number;
  text: string;
  tone: ToastTone;
}

/** 떠 있는 시간과 동시에 쌓이는 개수 상한(작업이 많아도 화면을 덮지 않게). */
const TOAST_MS = 4000;
const MAX_TOASTS = 4;

let nextId = 1;
const listeners = new Set<(item: ToastItem) => void>();

/**
 * 작업 관련 소식을 화면 오른쪽 아래에 띄운다.
 * 에이전트 작업 시작·완료·실패·질문, 새로고침처럼 **사용자가 알아야 하는 일**에 쓴다.
 * 컴포넌트 밖(스토어·화면 어디서든)에서 부를 수 있게 모듈 함수로 둔다.
 */
export function toast(text: string, tone: ToastTone = 'info'): void {
  const item: ToastItem = { id: nextId, text, tone };
  nextId += 1;
  listeners.forEach((listener) => listener(item));
}

/** 알림이 쌓이는 자리. `App` 이 한 번만 그린다. */
export default function Toaster() {
  const [items, setItems] = useState<ToastItem[]>([]);

  useEffect(() => {
    const onToast = (item: ToastItem) => {
      setItems((prev) => [...prev, item].slice(-MAX_TOASTS));
      window.setTimeout(() => {
        setItems((prev) => prev.filter((candidate) => candidate.id !== item.id));
      }, TOAST_MS);
    };
    listeners.add(onToast);
    return () => {
      listeners.delete(onToast);
    };
  }, []);

  if (items.length === 0) return null;

  return (
    <ul className={styles.host} aria-live="polite" aria-label="알림">
      {items.map((item) => (
        <li
          key={item.id}
          className={`${styles.toast} ${styles[item.tone]}`}
          onClick={() => setItems((prev) => prev.filter((candidate) => candidate.id !== item.id))}
          title="눌러서 닫기"
        >
          {item.text}
        </li>
      ))}
    </ul>
  );
}
