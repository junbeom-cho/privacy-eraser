export interface DbConnectionInput {
  url: string
  username: string
  password: string
  schema: string
}

export interface ConnectionTestResult {
  success: boolean
  message: string
}

export function emptyConnection(): DbConnectionInput {
  return { url: '', username: '', password: '', schema: '' }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(payload?.message ?? '요청에 실패했습니다.')
  }
  return payload as T
}

export function testConnection(connection: DbConnectionInput) {
  return post<ConnectionTestResult>('/api/projects/connection-test', connection)
}

/**
 * 이관 대상(edit_schema)은 여기서 받지 않습니다. 비식별화를 실행할 때 정합니다.
 */
export function createProject(name: string, rawConnection: DbConnectionInput) {
  return post<{ id: number }>('/api/projects', { name, rawConnection })
}
