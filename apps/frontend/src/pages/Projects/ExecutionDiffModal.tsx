import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useFeatureStore } from '../../lib/featureStore';
import exec from '../../styles/execution.module.css';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { Execution } from '../../types';

/**
 * 실행 트리 루트 행의 "변경 diff 보기"가 여는 창.
 * `GET /executions/{id}/diff` 가 주는 파일별 unified diff 를 파일 목록 + 본문으로 그린다.
 * API 가 아직 없으면 목업을 만들지 않고 대기 문구를 남긴다(실행 트리 창과 같은 패턴을 그대로 쓴다).
 */
export default function ExecutionDiffModal({
  execution,
  onClose,
}: {
  execution: Execution;
  onClose: () => void;
}) {
  const { diffOf, diffStatusOf, loadExecutionDiff } = useFeatureStore();
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => {
    loadExecutionDiff(execution.id);
  }, [execution.id]);

  const diff = diffOf(execution.id);
  const status = diffStatusOf(execution.id);
  const files = diff?.files ?? [];
  const current = files.find((file) => file.path === selected) ?? files[0] ?? null;

  return createPortal(
    <div className={modal.overlay} onClick={onClose}>
      <div className={exec.diffModal} role="dialog" aria-label="변경 diff" onClick={(e) => e.stopPropagation()}>
        <div>
          <h2>변경 diff</h2>
          <p className={exec.prompt}>
            실행 #{execution.id} · 트리가 바꾼 파일별 unified diff
            {execution.resultCommit ? ` · 커밋 ${execution.resultCommit}` : ''}
          </p>
        </div>
        {status === 'loading' && <p className={shared.muted}>불러오는 중…</p>}

        {status !== 'loading' && files.length === 0 && (
          <p className={shared.muted}>
            {status === 'unsupported'
              ? '변경 diff API(GET /executions/{id}/diff)를 기다리는 중입니다. API 가 열리면 파일별 diff 가 여기에 표시됩니다.'
              : status === 'error'
                ? 'diff 를 읽지 못했습니다.'
                : '표시할 변경 파일이 없습니다.'}
          </p>
        )}

        {files.length > 0 && (
          <div className={exec.diffScroll}>
            <div className={exec.diffFiles}>
              {files.map((file) => (
                <button
                  key={file.path}
                  type="button"
                  className={`${exec.diffFile} ${current?.path === file.path ? exec.diffFileOn : ''}`}
                  onClick={() => setSelected(file.path)}
                  title={file.path}
                >
                  {file.status} {file.path}
                </button>
              ))}
            </div>
            {current !== null && <pre className={exec.diffBody}>{current.diff}</pre>}
          </div>
        )}

        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}