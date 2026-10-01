import { useState } from 'react';
import { createPortal } from 'react-dom';
import { useMockStore } from '../../store/MockStore';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { AgentGroup } from '../../types';

/**
 * 그룹 프롬프트 편집 창. 프롬프트는 마스터 → 그룹 → 에이전트 순서로 겹쳐 적용된다.
 * 패널의 블러·스크롤에 잘리지 않도록 body 에 포털로 그린다.
 */
export default function GroupPromptModal({ group, onClose }: { group: AgentGroup; onClose: () => void }) {
  const { setGroupPrompt } = useMockStore();
  const [prompt, setPrompt] = useState(group.prompt);

  const onSave = () => {
    setGroupPrompt(group.id, prompt.trim());
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
