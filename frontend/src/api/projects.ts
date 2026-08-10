import { request } from './http'

export interface DbConnectionInput {
  url: string
  username: string
  password: string
  schema: string
}

/** 응답에는 비밀번호가 없습니다. 서버가 절대 내려주지 않습니다. */
export interface ConnectionView {
  url: string
  username: string
  schema: string
}

export interface ProjectView {
  id: number
  name: string
  rawConnection: ConnectionView
  editConnection: ConnectionView
}

export interface ConnectionTestResult {
  success: boolean
  message: string
}

/**
 * URL 은 비워두지 않고 형식이 보이는 값을 채워둡니다. 각자 환경에 맞게 고쳐 쓰는 편이
 * 빈 칸에 placeholder 만 보이는 것보다 빠릅니다.
 */
export function emptyConnection(): DbConnectionInput {
  return {
    url: 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1',
    username: '',
    password: '',
    schema: '',
  }
}

/** 조회 결과를 편집용으로 바꿉니다. 비밀번호는 받지 못하므로 빈 칸으로 둡니다. */
export function toInput(connection: ConnectionView | null | undefined): DbConnectionInput {
  if (!connection) return emptyConnection()
  return { url: connection.url, username: connection.username, password: '', schema: connection.schema }
}

export function testConnection(connection: DbConnectionInput) {
  return request<ConnectionTestResult>('POST', '/api/projects/connection-test', connection)
}

export function listProjects() {
  return request<ProjectView[]>('GET', '/api/projects')
}

export function getProject(id: number) {
  return request<ProjectView>('GET', `/api/projects/${id}`)
}

/**
 * 이관 대상(edit)도 함께 받습니다. 어차피 이관할 때 반드시 있어야 하는 값이라,
 * 선택값으로 두면 NULL 분기만 화면 전체로 번집니다. 계정이 아직 없어도 이름은 정할 수 있습니다.
 */
export function createProject(
  name: string,
  rawConnection: DbConnectionInput,
  editConnection: DbConnectionInput,
) {
  return request<{ id: number }>('POST', '/api/projects', { name, rawConnection, editConnection })
}

/** 비밀번호를 비워 보내면 서버가 기존 값을 유지합니다. */
export function updateProject(
  id: number,
  name: string,
  rawConnection: DbConnectionInput,
  editConnection: DbConnectionInput,
) {
  return request<void>('PUT', `/api/projects/${id}`, { name, rawConnection, editConnection })
}

export function deleteProject(id: number) {
  return request<void>('DELETE', `/api/projects/${id}`)
}

export type ColumnKey = 'PRIMARY_KEY' | 'UNIQUE' | 'FOREIGN_KEY'

/** PK·UNIQUE 컬럼을 마스킹하면 값이 겹쳐 이관할 때 제약조건을 걸 수 없습니다. */
export const KEY_BADGE: Record<ColumnKey, { label: string; className: string; title: string }> = {
  PRIMARY_KEY: {
    label: 'PK',
    className: 'text-bg-primary',
    title: '기본키입니다. 마스킹하면 값이 겹쳐 이관할 수 없습니다.',
  },
  UNIQUE: {
    label: 'UQ',
    className: 'text-bg-secondary',
    title: '고유키입니다. 마스킹하면 값이 겹쳐 이관할 수 없습니다.',
  },
  FOREIGN_KEY: {
    label: 'FK',
    className: 'text-bg-light border',
    title: '외래키입니다. 값이 겹쳐도 됩니다.',
  },
}

export interface ColumnView {
  name: string
  type: string
  nullable: boolean
  /** 컬럼명을 `_` 로 나눈 토큰. 키워드는 이것과 대조합니다. */
  tokens: string[]
  keys: ColumnKey[]
}

export interface TableView {
  name: string
  columnCount: number
  columns: ColumnView[]
}

/** 원본 스키마를 매번 실제 DB에서 읽습니다. 저장하지 않습니다. */
export function listTables(projectId: number) {
  return request<TableView[]>('GET', `/api/projects/${projectId}/tables`)
}
