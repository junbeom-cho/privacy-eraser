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

### 도커로 한 번에 띄우기

앱과 프로젝트 정보 DB를 같이 올립니다. Java도 Node도 없는 곳에서 이것만으로 돌아갑니다.

```bash
cp .env.example .env
docker compose up -d --build
```

`.env`에 `CREDENTIAL_SECRET`이 없으면 **compose가 시작하지 않습니다.** 기본값을 두지 않기 때문입니다.
이 값이 바뀌면 이미 저장된 raw/edit 비밀번호는 복호화할 수 없으니, 운영에서는 값을 고정해 두세요.

이미지는 3단계로 빌드됩니다. Vue를 빌드해 `src/main/resources/static`에 넣고 → Maven으로 jar를 말고 →
JRE 이미지에 jar 하나만 남깁니다(377MB, 비루트 실행).

`docker-compose.yaml`은 `infra/postgres` 정의를 `include`로 가져다 씁니다. 볼륨이 같아서
DB만 띄우던 개발 방식과 데이터를 공유합니다.

> **비식별화 대상 Oracle 주소에 주의하세요.** 컨테이너 안에서 `localhost`는 컨테이너 자신입니다.
> 프로젝트 접속 정보의 URL을 Oracle이 어디서 도는지에 맞춰 바꿔야 합니다.
>
> | Oracle 위치 | URL | 필요한 설정 |
> |---|---|---|
> | 호스트 | `jdbc:oracle:thin:@//host.docker.internal:1521/FREEPDB1` | 기본 포함 (`extra_hosts`) |
> | 도커 (`infra/oracle`) | `jdbc:oracle:thin:@//oracle23ai:1521/FREEPDB1` | `docker-compose.yaml`의 `networks` 주석 해제 |
>
> Oracle을 도커로 띄우면 compose 프로젝트가 달라 네트워크도 분리됩니다. 같은 네트워크에 붙여야
> 컨테이너 이름으로 서로를 찾습니다.

#### Windows (Docker Desktop만 있는 PC)

Node도 Java도 필요 없습니다. 빌드가 전부 컨테이너 안에서 일어나기 때문에, 소스와 Docker Desktop만
있으면 됩니다. 레지스트리도 필요 없습니다.

```powershell
git clone git@github.com:junbeom-cho/privacy-protector.git
cd privacy-protector
copy .env.example .env
docker compose up -d --build
```

`.env`의 `CREDENTIAL_SECRET`을 바꾸고 시작하세요. 이 값이 다르면 다른 PC에서 저장한 접속 정보를
가져와도 복호화하지 못합니다. **DB를 함께 옮길 계획이면 두 PC의 값을 똑같이 맞춰야 합니다.**

첫 빌드는 의존성을 받느라 몇 분 걸리고, 두 번째부터는 캐시가 걸려 빠릅니다.

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
