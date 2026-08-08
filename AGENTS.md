# AGENTS.md

## 프로젝트
DB 컬럼 비식별화(마스킹) 도구입니다. 요구사항은 [docs/PRD.md](docs/PRD.md)가 유일한 기준입니다.
PRD와 이 문서가 충돌하면 PRD가 우선이며, 코드 작업 전에 충돌을 먼저 알립니다.

## 스택
- Spring Boot **4.1.0**, Java **17**, Maven (wrapper)
- 프로젝트 정보 DB = **PostgreSQL** (`infra/postgres/docker-compose.yaml`), Flyway는 여기에만 적용합니다
- 타겟(raw/edit) = **Oracle**, 드라이버 `ojdbc11`. 런타임에 접속하므로 DataSource 빈이 아닙니다
- Spring Security 7 (Boot 4 라인). 현재 전 경로 `permitAll` — 아래 "시큐리티"를 참고하세요
- MyBatis: `org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0` (Boot 4 대응 라인. 3.0.x는 Boot 3용이라 쓰지 않습니다)
- 프론트엔드: `frontend/` — Vue 3 + TypeScript + vue-router + Vite + `sass-embedded`
- 설정 파일은 **yaml**(`application.yml`)입니다. `.properties`로 되돌리지 않습니다.
- Boot 3와 starter 이름이 다릅니다 (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`).
  의존성을 추가할 때는 Boot 4 기준 이름을 확인하고, 기억에 의존해 Boot 3 이름을 쓰지 않습니다.
- Java 17 문법까지만 씁니다. (record/sealed는 가능, virtual thread·Boot 3 이하 API는 금지)

## 명령어
```bash
docker compose -f infra/postgres/docker-compose.yaml --env-file .env up -d   # 프로젝트 정보 DB :5432
./mvnw test              # 테스트. DB 없이 전부 돕니다
./mvnw spring-boot:run   # 백엔드 :8080. 로컬은 환경변수 없이 그냥 뜹니다 (.env)
npm --prefix frontend run dev     # 프론트 개발서버 :5173, /api 는 :8080 으로 프록시
npm --prefix frontend run build   # src/main/resources/static 으로 빌드 → 백엔드가 그대로 서빙
```
새로 클론했다면 `cp .env.example .env`를 먼저 실행합니다. `.env`는 gitignore 대상이고, 운영에서는 파일 없이 환경변수를 씁니다.

- `.env` 한 파일을 **Spring과 docker compose가 같이 읽습니다.** Spring 쪽은 `optional:file:.env[.properties]`
  입니다 (`.env`와 `.properties`는 둘 다 `KEY=value`). 값이 한 곳에만 있으니 앱과 컨테이너가 어긋나지 않습니다.
  compose 쪽은 `--env-file .env`가 **반드시 필요합니다.** compose 파일이 `infra/` 아래라 루트 `.env`를
  자동으로 찾지 못합니다. 빠뜨리면 조용히 기본값이 쓰여서 인증 실패가 납니다.
- 그래서 `.env`에는 **`KEY=value`만** 씁니다. YAML 문법을 넣으면 compose가 `.env`를 파싱하다 깨집니다.
- 시크릿이 새로 필요해지면 `.env.example`에 **주석 처리된 예시**를 함께 추가합니다. 실제 값은 넣지 않습니다.
- `CREDENTIAL_SECRET`에는 절대 기본값을 주지 않습니다 — 안 주면 기동이 막혀야 합니다.
- `application.yml`에는 시크릿을 두지 않으므로 `.example` 사본을 만들지 않습니다. 사본은 원본과 어긋납니다.

설정 파일을 지우거나 옮긴 뒤에는 `./mvnw clean`을 먼저 실행합니다. `target/classes`에 남은 옛 파일이
`.yml`을 덮어써서 기동이 깨집니다 (`.properties`가 `.yml`보다 우선합니다).

## 구조 (DDD)
기능 단위(`project`, 이후 `keyword`, `masking` …) 아래에 네 계층을 둡니다. 의존 방향은 항상 안쪽(domain)으로만 향합니다.

| 계층 | 담는 것 | 금지 |
|------|---------|------|
| `domain` | 엔티티·값객체·불변식, 포트 인터페이스 | Spring/MyBatis/JDBC import 금지 |
| `application` | 유스케이스 조립, `@Transactional`, Command | SQL·HTTP 관심사 금지 |
| `infrastructure` | 포트 구현(매퍼·JDBC·암복호화) | 도메인 규칙 판정 금지 |
| `ui` | 컨트롤러, 요청/응답 DTO, 예외→상태코드 | 비즈니스 로직 금지 |

- 불변식은 도메인 생성자에서 던집니다. 서비스·컨트롤러에서 같은 검사를 반복하지 않습니다.
- 저장 시 DTO 변환은 infrastructure에서 합니다. 도메인에는 평문 비밀번호가, DB에는 암호문이 들어갑니다.

## 시큐리티
[SecurityConfig.java](src/main/java/kr/co/promptech/privacy_protector/config/SecurityConfig.java) — 인증 요구사항이 아직 없어 **전 경로 permitAll, CSRF off** 상태입니다.

- 인증(특히 쿠키/세션 기반)을 도입하는 작업에서는 **CSRF를 반드시 되살리고** `permitAll` 범위를 좁힙니다.
  지금 CSRF를 꺼둔 근거는 "탈 세션이 없다"는 것뿐이고, 세션이 생기는 순간 그 근거가 사라집니다.
- 기동 로그의 `Using generated security password`는 permitAll이라 쓰이지 않는 기본 사용자입니다. 인증을 넣으면 사라집니다.
- 사용자 계정 인증을 붙일 때 계정 비밀번호는 **Argon2id로 해싱**합니다
  (`Argon2PasswordEncoder`, BouncyCastle은 이미 의존성에 있습니다). bcrypt 기본값은 쓰지 않습니다.
- `@WebMvcTest`는 `SecurityConfig`를 자동으로 집어오지 않습니다. 컨트롤러 슬라이스 테스트에는
  `@Import(SecurityConfig.class)`를 붙입니다. 빠뜨리면 실제 필터체인이 아닌 기본 설정으로 테스트하게 됩니다.

## TDD
기능 코드보다 테스트를 먼저 씁니다. red 확인 → 구현 → green 확인 순서를 지킵니다.

- 도메인·애플리케이션 테스트는 순수 JUnit으로 씁니다. 목 프레임워크 대신 손으로 만든 Fake를 씁니다.
- 컨트롤러는 `@WebMvcTest` + `@MockitoBean` + `@Import(SecurityConfig.class)` 조합입니다.
- 매퍼 XML은 `ProjectMapperSqlTest`처럼 `BoundSql`로 바인딩까지 검증합니다. DB 없이 돕니다.
- **모든 테스트는 DB 없이 통과해야 합니다.** 실제 DB가 필요해지면 Testcontainers를 도입하고 이 규칙을 고칩니다.
- 테스트 메서드명은 한글로 사실을 서술합니다 (`raw와_edit이_같으면_저장하지_않는다`).

## 코드 규칙
- base package는 `kr.co.promptech.privacy_protector`이고, 기능 단위로 하위 패키지를 나눕니다.
- 사용자에게 보이는 예외 메시지는 존댓말로 끝맺습니다 (`~합니다.`).
- 새 라이브러리는 기본적으로 추가하지 않습니다. 필요하면 이유를 먼저 말하고 승인받습니다.
- 추상화는 구현체가 2개 이상 생길 때 만듭니다. 인터페이스 1개 + 구현 1개는 금지입니다.
- Flyway는 **프로젝트 정보 DB에만** 적용합니다. raw_schema / edit_schema는 Flyway 대상이 아닙니다.
  마이그레이션은 `src/main/resources/db/migration/V{n}__{설명}.sql`이고, 적용된 파일은 수정하지 않고 새 버전을 추가합니다.
- 테이블과 컬럼에는 `COMMENT ON`으로 설명을 답니다. 새 테이블을 만드는 마이그레이션에 함께 넣습니다.
  SQL 주석(`--`)은 파일에만 남지만 `COMMENT ON`은 DB에 저장되어 DB 도구에서 그대로 보입니다.
- 로그·예외 메시지에 실제 개인정보 값(원본 컬럼 데이터)을 절대 남기지 않습니다. 컬럼명까지만 남깁니다.

## MyBatis
- 값 바인딩은 항상 `#{}`를 씁니다. `${}`는 테이블·컬럼·스키마 **식별자에만** 허용합니다.
- `${}`에 넣는 식별자는 DB 메타데이터 조회 결과에 존재하는지 확인한 값만 씁니다.
  사용자 입력 문자열을 그대로 넘기면 SQL injection입니다. 검증은 한 곳에 모읍니다.
- 매퍼는 XML 하나로 통일합니다 (어노테이션 SQL과 섞지 않습니다). 동적 SQL이 많은 프로젝트라 XML이 맞습니다.
- 대량 복사는 한 건씩 INSERT 하지 않습니다. `ExecutorType.BATCH` + fetch size를 지정합니다.

## 프론트엔드
- API 경로는 전부 `/api` 아래에 둡니다. dev 프록시와 prod 서빙이 이 접두사에 맞춰져 있습니다.
- 스타일은 SFC의 `<style lang="scss" scoped>`에 씁니다. 전역 CSS 파일을 늘리지 않습니다.
- `src/main/resources/static/`은 빌드 산출물이라 gitignore 대상입니다. 여기에 직접 파일을 만들지 않습니다.
- 상태관리 라이브러리(Pinia 등)는 아직 없습니다. props/emit으로 안 되는 상황이 실제로 생기면 그때 넣습니다.
- API 응답의 컬럼 값은 이미 마스킹된 값입니다. 프론트에서 원본 값을 요청하거나 캐시하지 않습니다.

## 도메인 규칙 (깨지면 안 되는 것)
- 컬럼명은 `_` 기준으로 토큰을 분리한 뒤 키워드와 매칭합니다 (`T_usr_mstr` → `t`,`usr`,`mstr`).
- `Undo`가 우선입니다. `Do`와 `Undo`가 동시에 걸리면 **제외**합니다.
- 어느 키워드에도 안 걸리는 컬럼은 마스킹하지 않습니다 (기본값 = 비대상).
- 정책은 방향(앞/뒤) + 마스킹 문자 수로 이루어집니다. 마스킹 문자는 `*`입니다.
- 판정 로직(키워드 매칭 → 대상 여부 → 정책 적용)은 한 곳에만 둡니다. 호출부마다 조건 분기를 두지 않습니다.

## DB 3개 (Schema to Schema)
`raw_schema`에서 읽어 → 마스킹 → `edit_schema`에 씁니다. 원본은 건드리지 않습니다.

| # | 이름 | 역할 | 연결 방식 | 권한 |
|---|------|------|-----------|------|
| 1 | 프로젝트 정보 DB | 프로젝트·키워드·정책 저장 | PostgreSQL, `spring.datasource.*` | 읽기/쓰기, Flyway 대상 |
| 2 | `raw_schema` | 비식별화 대상 원본 | 프로젝트 레코드에 저장 → 런타임 연결 | **읽기 전용** |
| 3 | `edit_schema` | 비식별화 결과 이관처 | 프로젝트 레코드에 저장 → 런타임 연결 | 쓰기 |

- `raw_schema`에는 SELECT와 메타데이터 조회만 합니다. INSERT/UPDATE/DELETE/DDL은 금지입니다.
- `raw_schema == edit_schema`(같은 접속 + 같은 스키마명)이면 실행을 거부합니다. 이 가드가 없으면 원본을 덮어씁니다.
- 마스킹은 쓰기 직전 한 곳에서만 적용합니다. 판정을 거치지 않은 값이 `edit_schema`에 들어가는 경로가 있으면 안 됩니다.
- 2·3번 접속 정보는 `application.yml`에 적지 않습니다. 프로젝트 레코드에 저장하고 런타임에 연결합니다.
- 저장하는 접속 비밀번호는 `CredentialCipher`로 **암호화**합니다(AES-GCM, 키는 Argon2id 파생). 평문 컬럼은 금지입니다.
  **해싱하면 안 됩니다** — 타겟 Oracle에 이 비밀번호로 실제 접속해야 하므로 복호화가 가능해야 합니다.
- 키 재료는 `CREDENTIAL_SECRET` + `CREDENTIAL_SALT`입니다. 둘 중 하나라도 바뀌면 기존 행을 복호화할 수 없습니다.
  값을 교체해야 하면 재암호화 마이그레이션을 먼저 준비합니다.
- 메타데이터 조회는 선택한 스키마·테이블 범위 안에서만 합니다. 전체 DB 스캔은 금지입니다.

## 작업 방식
- 요청받은 범위만 구현합니다. "나중에 쓸 것 같아서" 만드는 코드는 금지입니다.
- 판정·마스킹 같은 로직에는 테스트를 1개 남깁니다.
  경계 케이스는 `Do`+`Undo` 동시 매칭, 마스킹 길이 > 값 길이, NULL 컬럼, 소스==타겟 거부입니다.
- 모르는 건 추측하지 말고 묻습니다. 특히 PRD에 없는 정책 세부사항이 그렇습니다.
- 문서와 대화는 `~합니다` 체로 씁니다.
