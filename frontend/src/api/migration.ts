import { request } from './http'

export type MigrationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

/** 마스킹 컬럼 하나의 결과. 표본 1행으로는 알 수 없는 전수 집계입니다. */
export interface ColumnMaskingStatView {
  tableName: string
  columnName: string
  totalRows: number
  fullyMaskedRows: number
}

export interface MigrationRunView {
  runId: number
  status: MigrationStatus
  totalTables: number
  completedTables: number
  currentTable: string | null
  message: string | null
  startedAt: string
  finishedAt: string | null
  stats: ColumnMaskingStatView[]
}

export const STATUS_LABEL: Record<MigrationStatus, string> = {
  RUNNING: '진행 중',
  SUCCEEDED: '완료',
  FAILED: '실패',
}

/** 실행 ID 만 돌려받고 상태는 따로 조회합니다. 수십 초~몇 분이 걸리기 때문입니다. */
export function startMigration(projectId: number) {
  return request<{ runId: number }>('POST', `/api/projects/${projectId}/migration`)
}

/** 가장 최근 실행. 아직 실행한 적이 없으면 null 입니다. */
export function latestMigration(projectId: number) {
  return request<MigrationRunView | null>('GET', `/api/projects/${projectId}/migration`)
}
