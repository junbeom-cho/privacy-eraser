/**
 * 서버 응답은 오류일 때 {"message": "..."} 형식입니다.
 * 화면은 이 message 를 그대로 사용자에게 보여줍니다.
 */
export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    headers: {
      // 명시하지 않으면 Accept 가 */* 라, 매핑이 없는 경로에서 JSON 대신 HTML 오류 페이지가 옵니다.
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(payload?.message ?? '요청에 실패했습니다.')
  }
  return payload as T
}

export function messageOf(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
