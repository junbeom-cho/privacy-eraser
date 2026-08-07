# AGENTS.md

## 프로젝트
DB 컬럼 비식별화(마스킹) 도구. 요구사항은 [docs/PRD.md](docs/PRD.md)가 유일한 기준이다.
PRD와 이 문서가 충돌하면 PRD가 우선이고, 코드 작업 전에 충돌을 먼저 알린다.

## 스택
- Spring Boot **4.1.0**, Java **17**, Maven (wrapper)
- Flyway (`flyway-database-oracle`), Oracle JDBC `ojdbc11`
- MyBatis: `org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0` (Boot 4 대응 라인. 3.0.x는 Boot 3용이므로 쓰지 않는다)
- 프론트엔드: `frontend/` — Vue 3 + TypeScript + vue-router + Vite + `sass-embedded`
- 설정 파일은 **yaml**(`application.yml`). `.properties`로 되돌리지 않는다.
- Boot 3와 starter 이름이 다르다 (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`).
  의존성 추가 시 Boot 4 기준 이름을 확인하고, 기억에 의존해 Boot 3 이름을 쓰지 않는다.
- Java 17 문법까지만. (record/sealed OK, virtual thread·Boot 3 이하 API 금지)

## 명령어
```bash
./mvnw test              # 테스트 (DB 없이 돈다 — src/test/resources/application.yml 참고)
./mvnw spring-boot:run   # 백엔드 :8080. PROJECT_DB_URL / PROJECT_DB_USER / PROJECT_DB_PASSWORD 환경변수 필요
npm --prefix frontend run dev     # 프론트 개발서버 :5173, /api 는 :8080 으로 프록시
npm --prefix frontend run build   # src/main/resources/static 으로 빌드 → 백엔드가 그대로 서빙
```

## 코드 규칙
- base package: `kr.co.promptech.privacy_protector`, 기능 단위로 하위 패키지를 나눈다.
- 새 라이브러리는 기본적으로 추가하지 않는다. 필요하면 이유를 먼저 말하고 승인받는다.
- 추상화는 구현체가 2개 이상 생길 때 만든다. 인터페이스 1개 + 구현 1개 금지.
- Flyway는 **프로젝트 정보 DB에만** 적용한다. raw_schema / edit_schema는 Flyway 대상이 아니다.
  마이그레이션은 `src/main/resources/db/migration/V{n}__{설명}.sql`, 적용된 파일은 수정하지 않고 새 버전을 추가.
- 로그/예외 메시지에 실제 개인정보 값(원본 컬럼 데이터)을 절대 남기지 않는다. 컬럼명까지만.

## MyBatis
- 값 바인딩은 항상 `#{}`. `${}`는 테이블·컬럼·스키마 **식별자에만** 허용한다.
- `${}`에 넣는 식별자는 반드시 DB 메타데이터 조회 결과에 존재하는지 확인한 값만 쓴다.
  사용자 입력 문자열을 그대로 넘기면 SQL injection이다. 검증은 한 곳에 모은다.
- 매퍼는 XML 하나로 통일한다 (어노테이션 SQL과 섞지 않는다). 동적 SQL이 많은 프로젝트라 XML이 맞다.
- 대량 복사는 한 건씩 INSERT 하지 않는다. `ExecutorType.BATCH` + fetch size 지정.

## 프론트엔드
- API 경로는 전부 `/api` 아래. dev 프록시와 prod 서빙이 이 접두사에 맞춰져 있다.
- 스타일은 SFC의 `<style lang="scss" scoped>`. 전역 CSS 파일을 늘리지 않는다.
- `src/main/resources/static/`은 빌드 산출물이라 gitignore 대상이다. 여기에 직접 파일을 만들지 않는다.
- 상태관리 라이브러리(Pinia 등)는 아직 없다. props/emit으로 안 되는 상황이 실제로 생기면 그때 넣는다.
- API 응답의 컬럼 값은 이미 마스킹된 값이다. 프론트에서 원본 값을 요청하거나 캐시하지 않는다.

## 도메인 규칙 (깨지면 안 되는 것)
- 컬럼명은 `_` 기준으로 토큰 분리 후 키워드와 매칭한다 (`T_usr_mstr` → `t`,`usr`,`mstr`).
- `Undo` 우선. `Do`와 `Undo`가 동시에 걸리면 **제외**한다.
- 어느 키워드에도 안 걸리는 컬럼은 마스킹하지 않는다 (기본값 = 비대상).
- 정책 = 방향(앞/뒤) + 마스킹 문자 수. 마스킹 문자는 `*`.
- 판정 로직(키워드 매칭 → 대상 여부 → 정책 적용)은 한 곳에만 둔다. 호출부마다 조건 분기 금지.

## DB 3개 (Schema to Schema)
`raw_schema`에서 읽어 → 마스킹 → `edit_schema`에 쓴다. 원본은 건드리지 않는다.

| # | 이름 | 역할 | 연결 방식 | 권한 |
|---|------|------|-----------|------|
| 1 | 프로젝트 정보 DB | 프로젝트·키워드·정책 저장 | `spring.datasource.*` (환경변수) | 읽기/쓰기, Flyway 대상 |
| 2 | `raw_schema` | 비식별화 대상 원본 | 프로젝트 레코드에 저장 → 런타임 연결 | **읽기 전용** |
| 3 | `edit_schema` | 비식별화 결과 이관처 | 프로젝트 레코드에 저장 → 런타임 연결 | 쓰기 |

- `raw_schema`에는 SELECT와 메타데이터 조회만. INSERT/UPDATE/DELETE/DDL 금지.
- `raw_schema == edit_schema`(같은 접속 + 같은 스키마명)이면 실행을 거부한다. 이 가드가 없으면 원본을 덮어쓴다.
- 마스킹은 쓰기 직전 한 곳에서만 적용한다. 판정을 거치지 않은 값이 `edit_schema`에 들어가는 경로가 있으면 안 된다.
- 2·3번 접속 정보는 `application.properties`에 적지 않는다. 프로젝트 레코드에 저장하고 런타임에 DataSource를 만든다.
- 메타데이터 조회는 선택한 스키마/테이블 범위 안에서만. 전체 DB 스캔 금지.

## 작업 방식
- 요청받은 범위만 구현한다. "나중에 쓸 것 같아서" 만드는 코드 금지.
- 판정·마스킹 같은 로직에는 테스트를 1개 남긴다.
  경계 케이스: `Do`+`Undo` 동시 매칭, 마스킹 길이 > 값 길이, NULL 컬럼, 소스==타겟 거부.
- 모르는 건 추측하지 말고 묻는다. 특히 PRD에 없는 정책 세부사항.
