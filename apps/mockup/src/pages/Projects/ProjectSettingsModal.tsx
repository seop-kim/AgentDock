import { FormEvent, useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { Project } from '../../types';
import ProjectWorkspaces from './ProjectWorkspaces';
import styles from './ProjectSettingsModal.module.css';

/** 프로젝트 상세 설정 창: 기본 정보(이름), 워크스페이스, 삭제. */
export default function ProjectSettingsModal({
  project,
  onClose,
  onDeleted,
}: {
  project: Project;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const { agents, groups, tasks, renameProject, deleteProject } = useMockStore();
  const [name, setName] = useState(project.name);

  const trimmed = name.trim();
  const changed = trimmed !== '' && trimmed !== project.name;

  const onRename = (e: FormEvent) => {
    e.preventDefault();
    if (changed) renameProject(project.id, trimmed);
  };

  const onDelete = () => {
    const counts = [
      agents.filter((a) => a.projectId === project.id).length,
      groups.filter((g) => g.projectId === project.id).length,
      tasks.filter((t) => t.projectId === project.id).length,
    ];
    const message = `"${project.name}" 프로젝트를 삭제할까요?\n에이전트 ${counts[0]}개, 그룹 ${counts[1]}개, Task ${counts[2]}개도 함께 삭제됩니다.`;
    if (!window.confirm(message)) return;
    deleteProject(project.id);
    onDeleted();
  };

  return (
    <div className={modal.overlay} onClick={onClose}>
      <div
        className={`${modal.modal} ${modal.wide}`}
        role="dialog"
        aria-label="프로젝트 설정"
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.header}>
          <h2>프로젝트 설정</h2>
          <button type="button" onClick={onClose}>
            닫기
          </button>
        </div>

        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>기본 정보</h3>
          <form onSubmit={onRename} className={styles.row}>
            <input value={name} onChange={(e) => setName(e.target.value)} aria-label="프로젝트 이름" required />
            <button type="submit" disabled={!changed}>
              저장
            </button>
          </form>
        </section>

        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>워크스페이스</h3>
          <p className={shared.hint}>
            ★ 기본 워크스페이스가 에이전트 실행 디렉터리가 됩니다. 여러 개를 할당할 수 있고 기본은 프로젝트당 하나입니다.
          </p>
          <ProjectWorkspaces project={project} />
        </section>

        <section className={`${styles.section} ${styles.danger}`}>
          <h3 className={styles.sectionTitle}>위험 영역</h3>
          <div className={styles.row}>
            <p className={styles.dangerText}>프로젝트와 그 안의 에이전트, 그룹, Task 가 모두 삭제됩니다.</p>
            <button type="button" className={shared.dangerButton} onClick={onDelete}>
              프로젝트 삭제
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
