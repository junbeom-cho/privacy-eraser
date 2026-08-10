<script setup lang="ts">
import { ref } from 'vue'

/**
 * 이관 대상 스키마를 만드는 방법을 안내합니다. 도구는 만들지 않습니다.
 * <p>
 * 그러려면 `CREATE USER` 권한이 필요한데, 그 권한을 가진 계정을 앱에 저장하면
 * 사고가 났을 때 피해가 이관 대상 스키마를 넘어 DB 전체로 번집니다.
 * <p>
 * 프로젝트마다 만들어 주지 않고 고정된 안내로 둡니다. 바꿀 것이 이름 몇 개라
 * 만들어 주는 값보다 그걸 만드는 코드를 유지하는 비용이 큽니다.
 */
const SCRIPT = `-- [1] DBA 계정(SYSTEM 등)으로 실행합니다.
CREATE USER <이관대상스키마> IDENTIFIED BY "<비밀번호>";
GRANT CONNECT, RESOURCE TO <이관대상스키마>;
-- 공간이 모자라면 적재 도중에 ORA-01950 으로 실패합니다.
GRANT UNLIMITED TABLESPACE TO <이관대상스키마>;

-- [2] 원본 계정으로 실행합니다. DBA 권한이 필요 없습니다.
-- 이관은 CREATE TABLE ... AS SELECT 를 이관 대상 계정으로 실행하므로
-- 그 계정이 원본을 읽을 수 있어야 합니다.
GRANT SELECT ANY TABLE ON SCHEMA <원본스키마> TO <이관대상스키마>;`

const copied = ref(false)

async function copy() {
  await navigator.clipboard.writeText(SCRIPT)
  copied.value = true
  window.setTimeout(() => (copied.value = false), 1500)
}
</script>

<template>
  <details class="card">
    <summary class="card-header small">이관 대상 스키마가 아직 없나요? — 만드는 방법</summary>
    <div class="card-body">
      <p class="text-body-secondary small">
        이 도구는 스키마를 <strong>만들지 않습니다.</strong> 그러려면 <code>CREATE USER</code> 권한이
        필요한데, 그 권한을 가진 계정을 앱에 저장하면 사고가 났을 때 피해가 DB 전체로 번집니다.
        아래를 DBA에게 전달하세요. <code>&lt;...&gt;</code> 부분만 바꾸면 됩니다.
      </p>

      <button type="button" class="btn btn-sm btn-outline-secondary mb-2" @click="copy">
        {{ copied ? '복사했습니다' : '복사' }}
      </button>
      <pre
        class="bg-body-tertiary border rounded p-2 small"
        style="max-height: 22rem; overflow: auto"
      ><code>{{ SCRIPT }}</code></pre>

      <p class="text-body-secondary small mb-1">
        마지막 줄의 <code>ON SCHEMA</code> 가 핵심입니다. 이 절이 붙으면 그 스키마 하나에만 걸리고,
        빠지면 <strong>DB 전체</strong>가 열립니다. 이름이 같아 헷갈리기 쉽습니다.
      </p>
      <ul class="small mb-1 ps-3">
        <li>
          <span class="text-success">써도 됩니다</span>
          <code>GRANT SELECT ANY TABLE <strong>ON SCHEMA &lt;원본스키마&gt;</strong> TO &lt;이관대상스키마&gt;</code>
        </li>
        <li>
          <span class="text-danger">쓰지 마세요</span>
          <code>GRANT SELECT ANY TABLE TO &lt;이관대상스키마&gt;</code> — 모든 스키마가 읽힙니다
        </li>
        <li>
          <span class="text-danger">쓰지 마세요</span>
          <code>GRANT ALL PRIVILEGES TO &lt;이관대상스키마&gt;</code> — <code>DROP ANY TABLE</code> 이
          딸려와, 스키마명을 잘못 넣으면 피해가 이관 대상 밖으로 번집니다
        </li>
      </ul>
      <p class="text-body-secondary small mb-0">
        <code>ON SCHEMA</code> 는 <strong>Oracle 23ai 이상</strong>에서만 됩니다. 그 아래 버전은
        테이블마다 <code>GRANT SELECT ON &lt;원본스키마&gt;.&lt;테이블&gt; TO &lt;이관대상스키마&gt;</code> 를 줘야 합니다.
      </p>
    </div>
  </details>
</template>
