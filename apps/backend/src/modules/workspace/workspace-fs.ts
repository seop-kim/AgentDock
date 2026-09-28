import { existsSync } from 'fs';
import { platform } from 'os';

export interface FsEntry {
  name: string;
  path: string;
}

/**
 * 폴더 선택 UI의 최상위 시작 지점을 반환한다.
 * Windows는 존재하는 드라이브 목록(C:\, D:\ ...), 그 외(macOS/Linux)는 루트(/) 하나를 반환한다.
 * 로컬 전용 도구이므로 경로 접근 자체를 제한하지 않는다 — 선택된 경로를 그대로 저장한다.
 */
export function listRoots(): FsEntry[] {
  if (platform() !== 'win32') {
    return [{ name: '/', path: '/' }];
  }

  const drives: FsEntry[] = [];
  for (let code = 65; code <= 90; code += 1) {
    const drivePath = `${String.fromCharCode(code)}:\\`;
    if (existsSync(drivePath)) {
      drives.push({ name: drivePath, path: drivePath });
    }
  }
  return drives;
}
