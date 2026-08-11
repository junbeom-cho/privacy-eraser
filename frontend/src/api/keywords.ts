import { request } from './http'

export type KeywordType = 'DO' | 'UNDO'
export type MaskingDirection = 'FROM_START' | 'FROM_END'

export interface KeywordView {
  id: number
  word: string
  type: KeywordType
  /** UNDO 는 제외가 전부라 정책이 없습니다. */
  direction: MaskingDirection | null
  length: number | null
}

export interface KeywordInput {
  word: string
  type: KeywordType
  direction: MaskingDirection | null
  length: number | null
}

export function emptyKeyword(): KeywordInput {
  return { word: '', type: 'DO', direction: 'FROM_END', length: 4 }
}

export const DIRECTION_LABEL: Record<MaskingDirection, string> = {
  FROM_START: '앞에서부터',
  FROM_END: '뒤에서부터',
}

/** 정책을 사람이 읽는 문장으로 만듭니다. */
export function describePolicy(keyword: KeywordView) {
  if (keyword.type === 'UNDO') return '마스킹 제외'
  if (!keyword.direction || !keyword.length) return '—'
  return `${DIRECTION_LABEL[keyword.direction]} ${keyword.length}자`
}

export function listKeywords(projectId: number) {
  return request<KeywordView[]>('GET', `/api/projects/${projectId}/keywords`)
}

export function createKeyword(projectId: number, keyword: KeywordInput) {
  return request<{ id: number }>('POST', `/api/projects/${projectId}/keywords`, keyword)
}

export function updateKeyword(projectId: number, keywordId: number, keyword: KeywordInput) {
  return request<void>('PUT', `/api/projects/${projectId}/keywords/${keywordId}`, keyword)
}

/** 프로젝트의 키워드를 전부 지웁니다. 되돌릴 수 없습니다. */
export function deleteAllKeywords(projectId: number) {
  return request<void>('DELETE', `/api/projects/${projectId}/keywords`)
}

export function deleteKeyword(projectId: number, keywordId: number) {
  return request<void>('DELETE', `/api/projects/${projectId}/keywords/${keywordId}`)
}
