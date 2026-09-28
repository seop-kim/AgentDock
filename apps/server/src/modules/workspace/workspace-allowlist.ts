import { resolve } from 'path';

/**
 * WORKSPACE_ALLOWED_ROOTS(콤마 구분)에 등록된 루트 경로 하위만 Workspace로 허용한다.
 * 임의 경로(C:\, /, /etc 등) 접근을 막기 위한 최소한의 검증이다.
 */
export function isPathAllowed(targetPath: string): boolean {
  const rootsEnv = process.env.WORKSPACE_ALLOWED_ROOTS ?? '';
  const roots = rootsEnv
    .split(',')
    .map((root) => root.trim())
    .filter(Boolean);

  if (roots.length === 0) {
    return false;
  }

  const resolvedTarget = resolve(targetPath);
  return roots.some((root) => {
    const resolvedRoot = resolve(root);
    return resolvedTarget === resolvedRoot || resolvedTarget.startsWith(resolvedRoot + '/');
  });
}
