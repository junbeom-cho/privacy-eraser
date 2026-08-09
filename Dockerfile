# 프론트(Vue) → 백엔드(Spring Boot) 순으로 빌드해 실행용 JRE 이미지에 jar 하나만 남깁니다.
# 프론트 산출물이 src/main/resources/static 으로 들어가야 백엔드가 그대로 서빙합니다.

# 1) 프론트엔드 --------------------------------------------------------------
FROM node:24-alpine AS frontend
WORKDIR /build

# package.json 만 먼저 복사해야 소스만 고쳤을 때 npm ci 를 다시 돌지 않습니다.
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN npm --prefix frontend ci

COPY frontend/ ./frontend/
# vite 의 outDir 이 ../src/main/resources/static 이라 /build/src/... 에 떨어집니다.
RUN npm --prefix frontend run build


# 2) 백엔드 ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build

# 의존성만 먼저 받아 캐시합니다. 소스가 바뀌어도 이 레이어는 그대로입니다.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ ./src/
COPY --from=frontend /build/src/main/resources/static/ ./src/main/resources/static/

# 테스트는 CI 에서 돌립니다. 이미지 빌드는 산출물을 만드는 일만 합니다.
RUN mvn -B -q clean package -DskipTests


# 3) 실행 --------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root 로 돌릴 이유가 없습니다.
RUN adduser --system --no-create-home eraser
USER eraser

# 이름에 버전이 붙어 있어 와일드카드로 받습니다. repackage 산출물은 이 하나뿐입니다.
COPY --from=backend /build/target/*.jar app.jar

EXPOSE 8080

# CREDENTIAL_SECRET 은 기본값이 없습니다. 없으면 여기서 바로 실패하는 것이 맞습니다.
# 이 값이 바뀌면 이미 저장된 raw/edit 비밀번호는 복호화할 수 없습니다.
ENTRYPOINT ["java", "-jar", "app.jar"]
