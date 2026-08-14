import { request } from './http'

export type KeywordType = 'DO' | 'UNDO'
export type MaskingDirection = 'FROM_START' | 'FROM_END'
/** 부분 마스킹은 값이 겹쳐 PK·UNIQUE 에 쓸 수 없습니다. 그런 컬럼에는 해시를 씁니다. */
export type MaskingType = 'PARTIAL' | 'HASH' | 'FIXED'

export const MASKING_TYPE_LABEL: Record<MaskingType, string> = {
  PARTIAL: '부분 마스킹',
  HASH: '해시',
  FIXED: '고정값',
}

export interface KeywordView {
  id: number
  word: string
  type: KeywordType
  maskingType: MaskingType | null
  fixedValue: string | null
  /** UNDO 는 제외가 전부라 정책이 없습니다. */
  direction: MaskingDirection | null
  length: number | null
}

export interface KeywordInput {
  word: string
  type: KeywordType
  maskingType: MaskingType
  fixedValue: string | null
  direction: MaskingDirection | null
  length: number | null
}

export function emptyKeyword(): KeywordInput {
  return { word: '', type: 'DO', maskingType: 'PARTIAL', fixedValue: null, direction: 'FROM_END', length: 4 }
}

export const DIRECTION_LABEL: Record<MaskingDirection, string> = {
  FROM_START: '앞에서부터',
  FROM_END: '뒤에서부터',
}

/** 정책을 사람이 읽는 문장으로 만듭니다. */
export function describePolicy(keyword: KeywordView) {
  if (keyword.type === 'UNDO') return '마스킹 제외'
  // 해시는 방향도 자릿수도 쓰지 않습니다.
  if (keyword.maskingType === 'HASH') return '해시'
  if (keyword.maskingType === 'FIXED') return `고정값 '${keyword.fixedValue}'`
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
