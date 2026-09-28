import { existsSync } from 'fs';
import { platform } from 'os';

export interface FsEntry {
  name: string;
  path: string;
}

/**
 * UNC/네트워크 경로(\\server\share, //server/share)를 거부한다.
 * 로컬 폴더 브라우징만 허용하는 게 의도이며, 네트워크 경로는 NTLM 인증 유출(SSRF성 자격 증명 탈취) 위험이 있다.
 */
export function isUncPath(path: string): boolean {
  return path.startsWith('\\\\') || path.startsWith('//');
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
