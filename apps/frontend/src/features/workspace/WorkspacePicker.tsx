import { useEffect, useState } from 'react';
import { WorkspaceBrowseEntry, api } from '../../lib/api';
import styles from './WorkspacePicker.module.css';

interface WorkspacePickerProps {
  onSelect: (path: string) => void;
  onClose: () => void;
}

export default function WorkspacePicker({ onSelect, onClose }: WorkspacePickerProps) {
  const [currentPath, setCurrentPath] = useState<string | null>(null);
  const [parentPath, setParentPath] = useState<string | null>(null);
  const [entries, setEntries] = useState<WorkspaceBrowseEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = (path?: string) => {
    setError(null);
    api
      .browseWorkspace(path)
      .then((res) => {
        setCurrentPath(res.path);
        setParentPath(res.parentPath);
        setEntries(res.entries);
      })
      .catch((e) => setError(String(e)));
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2>Workspace 폴더 선택</h2>
        <p className={styles.currentPath}>{currentPath ?? '드라이브 목록'}</p>
        {error && <p className="errorText">{error}</p>}
        <ul className={styles.list}>
          {parentPath !== null && (
            <li>
              <button type="button" onClick={() => load(parentPath)} className={styles.entryButton}>
                .. (상위 폴더)
              </button>
            </li>
          )}
          {entries.map((entry) => (
            <li key={entry.path}>
              <button type="button" onClick={() => load(entry.path)} className={styles.entryButton}>
                {entry.name}
              </button>
            </li>
          ))}
        </ul>
        <div className={styles.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="button" disabled={!currentPath} onClick={() => currentPath && onSelect(currentPath)}>
            이 폴더 선택
          </button>
        </div>
      </div>
    </div>
  );
}
