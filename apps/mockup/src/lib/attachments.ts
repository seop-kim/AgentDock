import type { AttachedFile } from '../types';

/**
 * 첨부한 파일을 **복사해 두는 프로젝트 안 폴더**(워크스페이스 기준 상대 경로).
 * 에이전트는 워크스페이스 폴더 밖을 볼 수 없으므로, 밖에서 끌어온 파일은 여기로 복사한 뒤 그 사본을 가리킨다.
 */
export const ATTACHMENT_FOLDER = '.agentdock/attachments';

/** 저장 이름 앞에 붙는 id 길이(전체 uuid 는 파일 이름으로 쓰기엔 길다). */
const ID_LENGTH = 8;

/**
 * 끌어온 파일을 저장할 때 쓸 이름. **같은 이름을 여러 번 붙여도 충돌하지 않게 앞에 id(uuid)를 붙이고**,
 * 원래 이름은 뒤에 남겨 사람이 알아볼 수 있게 한다(`3f9c1a7b-report.pdf`).
 * 실제 구현에서는 이 id 를 첨부 레코드의 키로 두고 원래 이름은 메타데이터로 보관한다.
 */
export function storedName(originalName: string): string {
  return `${newAttachmentId()}-${originalName}`;
}

/** 첨부 하나에 붙는 id. 새로 붙일 때마다 새로 만든다. */
export function newAttachmentId(): string {
  return crypto.randomUUID().slice(0, ID_LENGTH);
}

/** 저장 이름 → 프로젝트 폴더 안 경로. */
export function attachmentPath(name: string): string {
  return `${ATTACHMENT_FOLDER}/${name}`;
}

/**
 * 저장 이름에서 **원래 파일 이름을 되돌린다**(앞에 붙은 id 를 떼어 낸다).
 * 파일 목록에서는 실제 저장 이름을 보여 주고, 첨부 칩에서는 이 이름을 보여 준다.
 */
export function displayName(path: string): string {
  const name = path.split('/').pop() ?? path;
  const stripped = /^[0-9a-f]{8}-(.+)$/.exec(name);
  return stripped ? stripped[1] : name;
}

/** 이미 붙어 있는 첨부인지. 같은 워크스페이스의 같은 경로면 같은 파일이다. */
export function hasAttachment(list: AttachedFile[], file: AttachedFile): boolean {
  return list.some((item) => item.workspaceId === file.workspaceId && item.path === file.path);
}
