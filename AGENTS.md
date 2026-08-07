# AGENTS.md

## 프로젝트
DB 컬럼 비식별화(마스킹) 도구. 요구사항은 [docs/PRD.md](docs/PRD.md)가 유일한 기준이다.
PRD와 이 문서가 충돌하면 PRD가 우선이고, 코드 작업 전에 충돌을 먼저 알린다.

## 스택
- Spring Boot **4.1.0**, Java **17**, Maven (wrapper)
- 프로젝트 정보 DB = **PostgreSQL** (`docker-compose.yaml`), Flyway는 여기에만 적용
- 타겟(raw/edit) = **Oracle**, 드라이버 `ojdbc11`. 런타임에 접속하므로 DataSource 빈이 아니다
- Spring Security 7 (Boot 4 라인). 현재 전 경로 `permitAll` — 아래 "시큐리티" 참고
- MyBatis: `org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0` (Boot 4 대응 라인. 3.0.x는 Boot 3용이므로 쓰지 않는다)
- 프론트엔드: `frontend/` — Vue 3 + TypeScript + vue-router + Vite + `sass-embedded`
- 설정 파일은 **yaml**(`application.yml`). `.properties`로 되돌리지 않는다.
- Boot 3와 starter 이름이 다르다 (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`).
  의존성 추가 시 Boot 4 기준 이름을 확인하고, 기억에 의존해 Boot 3 이름을 쓰지 않는다.
- Java 17 문법까지만. (record/sealed OK, virtual thread·Boot 3 이하 API 금지)

## 명령어
```bash
docker compose -f infra/postgres/docker-compose.yaml --env-file .env up -d   # 프로젝트 정보 DB :5432
./mvnw test              # 테스트. DB 없이 전부 돈다
./mvnw spring-boot:run   # 백엔드 :8080. 로컬은 환경변수 없이 그냥 뜬다 (.env)
npm --prefix frontend run dev     # 프론트 개발서버 :5173, /api 는 :8080 으로 프록시
npm --prefix frontend run build   # src/main/resources/static 으로 빌드 → 백엔드가 그대로 서빙
```
새로 클론했다면 `cp .env.example .env` 먼저. `.env`는 gitignore 대상이고, 운영에서는 파일 없이 환경변수를 쓴다.

- `.env` 한 파일을 **Spring과 docker compose가 같이 읽는다.** Spring 쪽은 `optional:file:.env[.properties]`
  (`.env`와 `.properties`는 둘 다 `KEY=value`). 값이 한 곳에만 있으니 앱과 컨테이너가 어긋나지 않는다.
  compose 쪽은 `--env-file .env`가 **반드시 필요하다.** compose 파일이 `infra/` 아래라 루트 `.env`를
  자동으로 찾지 못한다. 빠뜨리면 조용히 기본값이 쓰여서 인증 실패가 난다.
- 그래서 `.env`에는 **`KEY=value`만** 쓴다. YAML 문법을 넣으면 compose가 `.env`를 파싱하다 깨진다.
- 시크릿이 새로 필요해지면 `.env.example`에 **주석 처리된 예시**를 함께 추가한다. 실제 값은 넣지 않는다.
- `CREDENTIAL_SECRET`에는 절대 기본값을 주지 않는다 — 안 주면 기동이 막혀야 한다.
- `application.yml`에는 시크릿을 두지 않으므로 `.example` 사본을 만들지 않는다. 사본은 원본과 어긋난다.

설정 파일을 지우거나 옮긴 뒤에는 `./mvnw clean`을 먼저 한다. `target/classes`에 남은 옛 파일이
`.yml`을 덮어써서 기동이 깨진다 (`.properties`가 `.yml`보다 우선한다).

## 구조 (DDD)
기능 단위(`project`, 이후 `keyword`, `masking` …) 아래에 네 계층을 둔다. 의존 방향은 항상 안쪽(domain)으로만.

| 계층 | 담는 것 | 금지 |
|------|---------|------|
| `domain` | 엔티티·값객체·불변식, 포트 인터페이스 | Spring/MyBatis/JDBC import 금지 |
| `application` | 유스케이스 조립, `@Transactional`, Command | SQL·HTTP 관심사 금지 |
| `infrastructure` | 포트 구현(매퍼·JDBC·암복호화) | 도메인 규칙 판정 금지 |
| `ui` | 컨트롤러, 요청/응답 DTO, 예외→상태코드 | 비즈니스 로직 금지 |

- 불변식은 도메인 생성자에서 던진다. 서비스·컨트롤러에서 같은 검사를 반복하지 않는다.
- 저장 시 DTO 변환은 infrastructure에서. 도메인에는 평문 비밀번호가, DB에는 암호문이 들어간다.

## 시큐리티
[SecurityConfig.java](src/main/java/kr/co/promptech/privacy_protector/config/SecurityConfig.java) — 인증 요구사항이 아직 없어 **전 경로 permitAll, CSRF off**.

- 인증(특히 쿠키/세션 기반)을 도입하는 작업에서는 **CSRF를 반드시 되살리고** `permitAll` 범위를 좁힌다.
  지금 CSRF를 꺼둔 근거는 "탈 세션이 없다"는 것뿐이고, 세션이 생기는 순간 근거가 사라진다.
- 기동 로그의 `Using generated security password`는 permitAll이라 쓰이지 않는 기본 사용자다. 인증을 넣으면 사라진다.
- 사용자 계정 인증을 붙일 때 계정 비밀번호는 **Argon2id로 해싱**한다
  (`Argon2PasswordEncoder`, BouncyCastle은 이미 의존성에 있다). bcrypt 기본값을 쓰지 않는다.
- `@WebMvcTest`는 `SecurityConfig`를 자동으로 집어오지 않는다. 컨트롤러 슬라이스 테스트에는
  `@Import(SecurityConfig.class)`를 붙인다. 빠뜨리면 실제 필터체인이 아닌 기본 설정으로 테스트하게 된다.

## TDD
기능 코드보다 테스트를 먼저 쓴다. red 확인 → 구현 → green 확인 순서를 지킨다.

- 도메인·애플리케이션 테스트는 순수 JUnit. 목 프레임워크 대신 손으로 만든 Fake를 쓴다.
- 컨트롤러는 `@WebMvcTest` + `@MockitoBean` + `@Import(SecurityConfig.class)`.
- 매퍼 XML은 `ProjectMapperSqlTest`처럼 `BoundSql`로 바인딩까지 검증한다. DB 없이 돈다.
- **모든 테스트는 DB 없이 통과해야 한다.** 실제 DB가 필요해지면 Testcontainers를 도입하고 여기 규칙을 고친다.
- 테스트 메서드명은 한글로 사실을 서술한다 (`raw와_edit이_같으면_저장하지_않는다`).

## 코드 규칙
- base package: `kr.co.promptech.privacy_protector`, 기능 단위로 하위 패키지를 나눈다.
- 사용자에게 보이는 예외 메시지는 존댓말로 끝맺는다 (`~합니다.`).
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
| 1 | 프로젝트 정보 DB | 프로젝트·키워드·정책 저장 | PostgreSQL, `spring.datasource.*` | 읽기/쓰기, Flyway 대상 |
| 2 | `raw_schema` | 비식별화 대상 원본 | 프로젝트 레코드에 저장 → 런타임 연결 | **읽기 전용** |
| 3 | `edit_schema` | 비식별화 결과 이관처 | 프로젝트 레코드에 저장 → 런타임 연결 | 쓰기 |

- `raw_schema`에는 SELECT와 메타데이터 조회만. INSERT/UPDATE/DELETE/DDL 금지.
- `raw_schema == edit_schema`(같은 접속 + 같은 스키마명)이면 실행을 거부한다. 이 가드가 없으면 원본을 덮어쓴다.
- 마스킹은 쓰기 직전 한 곳에서만 적용한다. 판정을 거치지 않은 값이 `edit_schema`에 들어가는 경로가 있으면 안 된다.
- 2·3번 접속 정보는 `application.yml`에 적지 않는다. 프로젝트 레코드에 저장하고 런타임에 연결한다.
- 저장하는 접속 비밀번호는 `CredentialCipher`로 **암호화**한다(AES-GCM, 키는 Argon2id 파생). 평문 컬럼 금지.
  **해싱하면 안 된다** — 타겟 Oracle에 이 비밀번호로 실제 접속해야 하므로 복호화가 가능해야 한다.
- 키 재료는 `CREDENTIAL_SECRET` + `CREDENTIAL_SALT`. 둘 중 하나라도 바뀌면 기존 행을 복호화할 수 없다.
  값을 교체해야 하면 재암호화 마이그레이션을 먼저 준비한다.
- 메타데이터 조회는 선택한 스키마/테이블 범위 안에서만. 전체 DB 스캔 금지.

## 작업 방식
- 요청받은 범위만 구현한다. "나중에 쓸 것 같아서" 만드는 코드 금지.
- 판정·마스킹 같은 로직에는 테스트를 1개 남긴다.
  경계 케이스: `Do`+`Undo` 동시 매칭, 마스킹 길이 > 값 길이, NULL 컬럼, 소스==타겟 거부.
- 모르는 건 추측하지 말고 묻는다. 특히 PRD에 없는 정책 세부사항.
