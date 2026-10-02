import { useState } from 'react';
import { createPortal } from 'react-dom';
import { useAgentDockStore } from '../../store/AgentDockStore';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { AgentGroup } from '../../types';

/**
 * 그룹 프롬프트 편집 창. 프롬프트는 마스터 → 그룹 → 에이전트 순서로 겹쳐 적용된다.
 * 같은 창에서 **공유 노트**(규칙이 아니라 그룹 안 실행들이 함께 보는 맥락)도 보고 고칠 수 있다 —
 * 노트는 트리가 끝날 때 요약 한 줄이 자동으로 덧붙으므로, 여기서는 읽고 정리하는 자리다.
 * 패널의 블러·스크롤에 잘리지 않도록 body 에 포털로 그린다.
 */
export default function GroupPromptModal({ group, onClose }: { group: AgentGroup; onClose: () => void }) {
  const { setGroupPrompt, setGroupNote } = useAgentDockStore();
  const [prompt, setPrompt] = useState(group.prompt);
  const [note, setNote] = useState(group.sharedNote);

  const onSave = () => {
    setGroupPrompt(group.id, prompt.trim());
    if (note !== group.sharedNote) {
      setGroupNote(group.id, note.trim());
    }
    onClose();
  };

  return createPortal(
    <div className={modal.overlay} onClick={onClose}>
      <div
        className={modal.modal}
        role="dialog"
        aria-label={`${group.name} 프롬프트`}
        onClick={(e) => e.stopPropagation()}
      >
        <h2>{group.name} 프롬프트</h2>
        <p className={shared.hint}>
          이 팀(그룹)이 일하는 방식입니다. <strong>마스터 → 그룹 → 에이전트</strong> 순서로 겹쳐 적용됩니다.
        </p>
        <textarea
          rows={5}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="예: 서버 코드는 테스트를 먼저 쓰고, 작은 단위로 자주 커밋한다."
          aria-label="그룹 프롬프트"
        />

        <h3>공유 노트</h3>
        <p className={shared.hint}>
          규칙이 아니라 <strong>맥락</strong>입니다. 이 팀의 실행들이 프롬프트에 함께 받아 보고,
          트리가 끝날 때 요약 한 줄이 자동으로 붙습니다(최근 것만 남습니다). 필요하면 여기서 정리하세요.
        </p>
        <textarea
          rows={5}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="예: #103 로그 기능 추가 — 로그 파일은 logs/agentdock.log, 20MB 롤링"
          aria-label="그룹 공유 노트"
        />
        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="button" onClick={onSave}>
            저장
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
