import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { FileIcon } from '../../components/icons';
import { ATTACHMENT_FOLDER, displayName, fileName } from '../../lib/attachments';
import { useAgentDockStore } from '../../store/AgentDockStore';
import attach from '../../styles/attachment.module.css';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { AttachedFile, Project } from '../../types';

/**
 * 명령에 붙일 파일 고르기. **프로젝트 워크스페이스 폴더 안의 파일만** 붙일 수 있다
 * (실제 제품에서 에이전트가 볼 수 있는 것도 그 폴더뿐이다). 워크스페이스 폴더를 실제로 읽어 목록을 보여 준다.
 */
export default function AttachFilesModal({
  project,
  attached,
  onClose,
  onApply,
}: {
  project: Project;
  attached: AttachedFile[];
  onClose: () => void;
  onApply: (files: AttachedFile[]) => void;
}) {
  const { workspaces, workspaceFiles, loadWorkspaceFiles } = useAgentDockStore();
  const initialWorkspaceId = project.workspaces.find((w) => w.isDefault)?.workspaceId ?? project.workspaces[0]?.workspaceId ?? 0;
  const [workspaceId, setWorkspaceId] = useState(initialWorkspaceId);
  const [picked, setPicked] = useState<AttachedFile[]>(attached);

  // 파일 목록은 **이 창이 열려 있는 동안에만** 필요하다 — 열릴 때(워크스페이스를 바꿀 때) 그때 읽는다.
  useEffect(() => {
    loadWorkspaceFiles(workspaceId);
  }, [loadWorkspaceFiles, workspaceId]);

  const workspace = workspaces.find((w) => w.id === workspaceId);
  const files = workspaceFiles[workspaceId] ?? [];
  const folders = groupByFolder(files);

  const isPicked = (path: string) => picked.some((file) => file.workspaceId === workspaceId && file.path === path);
  const toggle = (path: string) =>
    setPicked((prev) =>
      prev.some((file) => file.workspaceId === workspaceId && file.path === path)
        ? prev.filter((file) => !(file.workspaceId === workspaceId && file.path === path))
        : [...prev, { id: 0, workspaceId, path, name: displayName(path) }],
    );

  return createPortal(
    <div className={modal.overlay} onClick={onClose}>
      <div className={attach.picker} role="dialog" aria-label="파일 첨부" onClick={(e) => e.stopPropagation()}>
        <div>
          <h2>파일 첨부</h2>
          <p className={shared.hint}>
            프로젝트 워크스페이스 폴더 안의 파일 목록입니다. 여기 없는 파일은 <strong>채팅 창에 끌어다 놓으면</strong>{' '}
            <code>{ATTACHMENT_FOLDER}</code> 로 복사되어 붙습니다. 붙인 파일은 <strong>라우팅(경로·확장자)</strong>과 실행
            지시에 함께 쓰입니다.
          </p>
        </div>

        {project.workspaces.length > 1 && (
          <label className={attach.field}>
            <span>워크스페이스</span>
            <select value={workspaceId} onChange={(e) => setWorkspaceId(Number(e.target.value))} aria-label="워크스페이스 선택">
              {project.workspaces.map((info) => (
                <option key={info.workspaceId} value={info.workspaceId}>
                  {workspaces.find((w) => w.id === info.workspaceId)?.name ?? '워크스페이스'}
                  {info.isDefault ? ' ★' : ''}
                </option>
              ))}
            </select>
          </label>
        )}

        {workspace && <p className={attach.root}>{workspace.path}</p>}

        {files.length === 0 ? (
          <p className={shared.muted}>이 워크스페이스에는 붙일 파일이 없습니다.</p>
        ) : (
          <div className={attach.tree}>
            {folders.map((folder) => (
              <div key={folder.name} className={attach.folderGroup}>
                <span className={attach.folder}>{folder.name}</span>
                <ul className={attach.files}>
                  {folder.files.map((path) => (
                    <li key={path}>
                      <label className={attach.file} title={path}>
                        <input type="checkbox" checked={isPicked(path)} onChange={() => toggle(path)} />
                        <FileIcon size={15} />
                        <span>{fileName(path)}</span>
                      </label>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        )}

        <div className={modal.actions}>
          <span className={attach.count}>{picked.length}개 선택됨</span>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="button" onClick={() => onApply(picked)}>
            첨부
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/** 경로를 폴더별로 묶는다(폴더가 없으면 루트). */
function groupByFolder(files: string[]): { name: string; files: string[] }[] {
  const map = new Map<string, string[]>();
  files.forEach((path) => {
    const index = path.lastIndexOf('/');
    const folder = index === -1 ? '' : path.slice(0, index);
    map.set(folder, [...(map.get(folder) ?? []), path]);
  });
  return [...map.entries()].map(([folder, paths]) => ({ name: folder === '' ? '(루트)' : folder, files: paths }));
}
