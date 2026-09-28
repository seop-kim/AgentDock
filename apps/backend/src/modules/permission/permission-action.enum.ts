/**
 * PermissionProfile의 boolean 필드와 1:1 대응하는 액션 식별자.
 * Execution 실행 전 enforcement 검사에서 사용한다.
 */
export enum PermissionAction {
  FILE_READ = 'fileRead',
  FILE_WRITE = 'fileWrite',
  TERMINAL_EXECUTE = 'terminalExecute',
  GIT_STATUS = 'gitStatus',
  GIT_DIFF = 'gitDiff',
  GIT_COMMIT = 'gitCommit',
  GIT_PUSH = 'gitPush',
  DB_READ = 'dbRead',
  DB_WRITE = 'dbWrite',
  DB_SCHEMA_CHANGE = 'dbSchemaChange',
  DEPLOY = 'deploy',
  EXTERNAL_NETWORK_ACCESS = 'externalNetworkAccess',
}
