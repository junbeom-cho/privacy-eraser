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
  editConnection: ConnectionView | null
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
export function toInput(connection: ConnectionView | null): DbConnectionInput {
  if (!connection) return emptyConnection()
  return { url: connection.url, username: connection.username, password: '', schema: connection.schema }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(payload?.message ?? '요청에 실패했습니다.')
  }
  return payload as T
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

/** 이관 대상(edit)은 생성 시점에 받지 않습니다. 비식별화를 실행할 때 정합니다. */
export function createProject(name: string, rawConnection: DbConnectionInput) {
  return request<{ id: number }>('POST', '/api/projects', { name, rawConnection })
}

/** 비밀번호를 비워 보내면 서버가 기존 값을 유지합니다. */
export function updateProject(
  id: number,
  name: string,
  rawConnection: DbConnectionInput,
  editConnection: DbConnectionInput | null,
) {
  return request<void>('PUT', `/api/projects/${id}`, { name, rawConnection, editConnection })
}

export function deleteProject(id: number) {
  return request<void>('DELETE', `/api/projects/${id}`)
}

export interface ColumnView {
  name: string
  type: string
  nullable: boolean
  /** 컬럼명을 `_` 로 나눈 토큰. 키워드는 이것과 대조합니다. */
  tokens: string[]
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
