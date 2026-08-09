import { request } from './http'
import type { ColumnKey } from './projects'
import type { MaskingDirection } from './keywords'

export type DecisionSource = 'USER' | 'UNDO_KEYWORD' | 'DO_KEYWORD' | 'NO_MATCH'

export interface ColumnReviewView {
  tableName: string
  columnName: string
  type: string
  nullable: boolean
  tokens: string[]
  keys: ColumnKey[]
  /** 마스킹 대상인데 PK·UNIQUE 입니다. 이대로면 이관을 시작할 수 없습니다. */
  uniqueConflict: boolean
  masked: boolean
  direction: MaskingDirection | null
  length: number | null
  source: DecisionSource
  /** 걸린 키워드. 판정 근거로 보여줍니다. */
  matchedKeyword: string | null
  /** 정책이 컬럼 길이보다 길어 값 전체가 가려질 수 있습니다. */
  policyExceedsLength: boolean
  /** 원본에서 읽은 실제 값입니다. 진짜 개인정보이므로 화면에만 씁니다. */
  sample: string | null
  /** 표본에 정책을 적용한 결과입니다. */
  maskedSample: string | null
  /** 표본이 통째로 가려집니다. 정책이 값보다 길다는 뜻입니다. */
  sampleFullyMasked: boolean
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

/** 바뀐 줄을 돌려받습니다. 화면은 전체를 다시 부르지 않고 그 줄만 갈아끼웁니다. */
export function overrideColumn(
  projectId: number,
  tableName: string,
  columnName: string,
  body: { masked: boolean; direction: MaskingDirection | null; length: number | null },
) {
  return request<ColumnReviewView>(
    'PUT',
    `/api/projects/${projectId}/review/${tableName}/${columnName}`,
    body,
  )
}

/** 사용자 지정을 모두 지웁니다. 바뀌는 줄이 많아 되돌린 전체 목록을 돌려받습니다. */
export function clearAllOverrides(projectId: number) {
  return request<ColumnReviewView[]>('DELETE', `/api/projects/${projectId}/review`)
}

/** 사용자 지정을 지우면 다시 키워드 판정을 따릅니다. 되돌린 줄을 돌려받습니다. */
export function clearOverride(projectId: number, tableName: string, columnName: string) {
  return request<ColumnReviewView>(
    'DELETE',
    `/api/projects/${projectId}/review/${tableName}/${columnName}`,
  )
}
