import { FormEvent, useState } from 'react';
import { createPortal } from 'react-dom';
import { useMockStore } from '../../store/MockStore';
import modal from '../../styles/modal.module.css';
import type { Project } from '../../types';

/**
 * 새 그룹 만들기 창. 왼쪽 패널(글래스/스크롤)에 갇히지 않도록 body 에 포털로 그린다.
 * 그룹은 이름만 받는다(멤버는 만든 뒤 끌어 놓거나 리더가 자동 지정된다).
 */
export default function GroupFormModal({ project, onClose }: { project: Project; onClose: () => void }) {
  const { createGroup } = useMockStore();
  const [name, setName] = useState('');

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createGroup(project.id, name.trim());
    onClose();
  };

  return createPortal(
    <div className={modal.overlay} onClick={onClose}>
      <form className={modal.modal} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>새 그룹</h2>
        <div className={modal.field}>
          <label>이름</label>
          <input
            autoFocus
            placeholder="예: Backend Team"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>
        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={name.trim() === ''}>
            만들기
          </button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
