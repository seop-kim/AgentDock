import { ThemeChoice, useTheme } from '../../store/ThemeContext';
import shared from '../../styles/shared.module.css';
import styles from './Settings.module.css';

const OPTIONS: { value: ThemeChoice; label: string; description: string }[] = [
  { value: 'system', label: '시스템', description: '운영체제/브라우저의 라이트·다크 설정을 따릅니다.' },
  { value: 'light', label: '라이트', description: '항상 밝은 화면을 씁니다.' },
  { value: 'dark', label: '다크', description: '항상 어두운 화면을 씁니다.' },
];

export default function ThemeSettings() {
  const { theme, setTheme } = useTheme();

  return (
    <div>
      <h1>테마</h1>
      <p className={shared.hint}>선택한 테마는 이 브라우저에 저장되어 다시 열어도 유지됩니다.</p>
      <div className={styles.themeOptions} role="radiogroup" aria-label="테마">
        {OPTIONS.map(({ value, label, description }) => (
          <label
            key={value}
            className={`${styles.themeOption} ${theme === value ? styles.themeOptionActive : ''}`}
          >
            <input type="radio" name="theme" value={value} checked={theme === value} onChange={() => setTheme(value)} />
            <span className={styles.themeLabel}>{label}</span>
            <span className={shared.muted}>{description}</span>
          </label>
        ))}
      </div>
    </div>
  );
}
