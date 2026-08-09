import { request } from './http'

export type MigrationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface MigrationRunView {
  runId: number
  status: MigrationStatus
  totalTables: number
  completedTables: number
  currentTable: string | null
  message: string | null
  startedAt: string
  finishedAt: string | null
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
