# Privacy Eraser

DB의 개인정보 컬럼을 GUI로 비식별화(마스킹)하는 도구입니다.

원본 스키마(`raw_schema`)를 읽어 마스킹한 뒤 결과를 별도 스키마(`edit_schema`)에 이관합니다.
**원본은 절대 수정하지 않습니다.**

```
raw_schema  ──읽기──▶  키워드 판정 → 마스킹  ──쓰기──▶  edit_schema
 (읽기 전용)                                            (이관 대상)
```

컬럼 표준화가 잘 된 DB를 전제로, 컬럼명을 `_` 기준으로 쪼갠 토큰과 키워드를 매칭해 대상을 정합니다.
키워드는 `Do`(마스킹 대상) / `Undo`(제외)로 나뉘고, 둘 다 걸리면 **`Undo`가 이깁니다**.

자세한 요구사항은 [docs/PRD.md](docs/PRD.md)를 참고하세요.

## 스택

| | |
|---|---|
| 백엔드 | Spring Boot 4.1.0 / Java 17 / Maven |
| 영속성 | MyBatis 3.5.19 + Flyway 12.4.0 |
| 보안 | Spring Security 7.1.0 (현재 전 경로 permitAll) / BouncyCastle 1.85.2 (Argon2id) |
| 프론트엔드 | Vue 3.5 + TypeScript + vue-router 5 + Vite 8 + SASS |
| DB | PostgreSQL 18 (프로젝트 정보) / Oracle (비식별화 대상) |

## DB 3개

| # | 이름 | 역할 | 권한 |
|---|---|---|---|
| 1 | 프로젝트 정보 DB | 프로젝트·키워드·정책 저장. Flyway 대상 | 읽기/쓰기 |
| 2 | `raw_schema` | 비식별화 대상 원본 | **읽기 전용** |
| 3 | `edit_schema` | 비식별화 결과 이관처 | 쓰기 |

2·3번 접속 정보는 설정 파일이 아니라 프로젝트 레코드에 저장하고 런타임에 연결합니다.
접속 비밀번호는 AES-GCM으로 암호화해서 저장하며, 키는 Argon2id로 파생합니다.

## 시작하기

```bash
cp .env.example .env
docker compose -f infra/postgres/docker-compose.yaml --env-file .env up -d
./mvnw spring-boot:run
```

http://localhost:8080 으로 접속하면 됩니다. Flyway가 기동할 때 스키마를 만듭니다.

프론트엔드를 수정할 때는 개발 서버를 씁니다. HMR이 돌고 `/api` 요청은 :8080으로 프록시됩니다.

```bash
npm --prefix frontend install
npm --prefix frontend run dev     # http://localhost:5173
```

수정 내용을 :8080에 반영하려면 빌드해야 합니다. 결과물은 Spring이 그대로 서빙합니다.

```bash
npm --prefix frontend run build
```

### 테스트

```bash
./mvnw test
```

DB 없이 전부 돕니다. 매퍼 XML까지 `BoundSql`로 바인딩을 검증합니다.

## 구조

```
├── src/main/java/…/project/   기능 단위 패키지 (DDD 4계층)
│   ├── domain/                엔티티·값객체·포트. 프레임워크 의존 없음
│   ├── application/           유스케이스, 트랜잭션 경계
│   ├── infrastructure/        매퍼·JDBC·암복호화
│   └── ui/                    컨트롤러, 요청/응답 DTO
├── src/main/resources/
│   ├── db/migration/          Flyway (프로젝트 정보 DB 전용)
│   ├── mapper/                MyBatis XML
│   └── static/                프론트 빌드 산출물 (gitignore)
├── frontend/                  Vue 3 + Vite
├── infra/postgres/            프로젝트 정보 DB compose
├── infra/oracle/              타겟 DB compose (예정)
└── docs/                      요구사항
```

## 설정

- `.env` (gitignore) — 로컬 시크릿입니다. Spring과 docker compose가 **같이 읽습니다**
- [.env.example](.env.example) — 위 파일의 템플릿입니다
- `src/main/resources/application.yml` — 시크릿이 없습니다. 기본값은 로컬 개발 기준입니다

`CREDENTIAL_SECRET`에는 기본값이 없습니다. 없으면 기동이 막히는데, 의도된 동작입니다.
이 값이나 `CREDENTIAL_SALT`가 바뀌면 **이미 저장된 접속 비밀번호를 복호화할 수 없습니다.**

## 문서

| 문서 | 내용 |
|---|---|
| [docs/PRD.md](docs/PRD.md) | 요구사항입니다. 충돌하면 이 문서가 우선합니다 |
| [AGENTS.md](AGENTS.md) | 개발 규칙입니다. 아키텍처·TDD·보안·함정을 모아뒀습니다 |
| [CLAUDE.md](CLAUDE.md) | AI 도구용 진입점입니다 (AGENTS.md를 가리킵니다) |
