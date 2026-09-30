import type { AttachedFile } from '../types';

/**
 * 첨부한 파일을 **복사해 두는 프로젝트 안 폴더**(워크스페이스 기준 상대 경로).
 * 에이전트는 워크스페이스 폴더 밖을 볼 수 없으므로, 밖에서 끌어온 파일은 여기로 복사한 뒤 그 사본을 가리킨다.
 */
export const ATTACHMENT_FOLDER = '.agentdock/attachments';

/** 끌어온 파일 이름 → 프로젝트 폴더 안에 복사될 경로. */
export function attachmentPath(name: string): string {
  return `${ATTACHMENT_FOLDER}/${name}`;
}

/** 우리가 복사해 둔 첨부인지(폴더 안에 있으면 그렇다). */
export function isCopiedAttachment(path: string): boolean {
  return path.startsWith(`${ATTACHMENT_FOLDER}/`);
}

/** 이미 붙어 있는 첨부인지. 같은 워크스페이스의 같은 경로면 같은 파일이다. */
export function hasAttachment(list: AttachedFile[], file: AttachedFile): boolean {
  return list.some((item) => item.workspaceId === file.workspaceId && item.path === file.path);
}
