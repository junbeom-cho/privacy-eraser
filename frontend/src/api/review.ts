import { request } from './http'
import type { MaskingDirection } from './keywords'

export type DecisionSource = 'USER' | 'UNDO_KEYWORD' | 'DO_KEYWORD' | 'NO_MATCH'

export interface ColumnReviewView {
  tableName: string
  columnName: string
  type: string
  nullable: boolean
  tokens: string[]
  masked: boolean
  direction: MaskingDirection | null
  length: number | null
  source: DecisionSource
  /** 걸린 키워드. 판정 근거로 보여줍니다. */
  matchedKeyword: string | null
  /** 정책이 컬럼 길이보다 길어 값 전체가 가려질 수 있습니다. */
  policyExceedsLength: boolean
}

export const SOURCE_LABEL: Record<DecisionSource, string> = {
  USER: '직접 지정',
  UNDO_KEYWORD: 'Undo 키워드',
  DO_KEYWORD: 'Do 키워드',
  NO_MATCH: '미매칭',
}

export function listReview(projectId: number) {
  return request<ColumnReviewView[]>('GET', `/api/projects/${projectId}/review`)
}

export function overrideColumn(
  projectId: number,
  tableName: string,
  columnName: string,
  body: { masked: boolean; direction: MaskingDirection | null; length: number | null },
) {
  return request<void>('PUT', `/api/projects/${projectId}/review/${tableName}/${columnName}`, body)
}

/** 사용자 지정을 지우면 다시 키워드 판정을 따릅니다. */
export function clearOverride(projectId: number, tableName: string, columnName: string) {
  return request<void>('DELETE', `/api/projects/${projectId}/review/${tableName}/${columnName}`)
}
