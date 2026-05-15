# Vintage Hub Backend

Vintage Hub는 여러 빈티지 쇼핑몰의 공개 상품 정보를 수집하고 정규화해, 한 곳에서 상품을 검색하고 실측 기준으로 필터링할 수 있게 하는 백엔드 프로젝트입니다.

현재는 Spring Boot 단일 애플리케이션으로 시작하며, 로켓샐러드 크롤링, 상품 목록 조회, 상품 상세 조회, 관리자 수동 크롤 실행 API를 중심으로 개발되어 있습니다.

## 기술 스택

- Java 25
- Spring Boot 4.0.6
- Gradle
- PostgreSQL 17
- Spring Data JPA
- QueryDSL
- Flyway
- Jsoup

## 파일 구조

```text
src/main/java/com/joypeb/vintagehub
├── common
│   └── api                 # 공통 응답, 오류 코드, 전역 예외 처리
├── crawl
│   ├── application         # 크롤 실행 유스케이스
│   ├── domain              # 크롤러 공통 계약과 수집 모델
│   ├── persistence         # 크롤 사이트/실행 엔티티와 Repository
│   └── site/rocketsalad    # 로켓샐러드 크롤러 구현
├── docs                    # OpenAPI 설정
├── product
│   ├── api                 # 상품 조회 컨트롤러와 응답 DTO
│   ├── application         # 상품 검색/상세 조회 서비스
│   └── persistence         # 상품/실측 엔티티, Repository, Specification
└── VintageHubBeApplication.java
```

주요 문서는 `docs/` 아래에 있습니다.

- `docs/design.md`: 제품 기획과 MVP 범위
- `docs/architecture.md`: 백엔드 아키텍처
- `docs/internal-api.md`: 현재 API 명세
- `docs/crawlability-report.md`: 크롤링 가능성 검토
- `docs/crawling-policy/`: 사이트별 크롤링 정책

## 실행

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

테스트:

```bash
./gradlew test
```

## 개발 규칙

- Gradle은 시스템 설치본이 아니라 `./gradlew`를 사용합니다.
- 기능별 패키지 경계를 유지합니다.
- 컨트롤러 DTO를 서비스 계층까지 그대로 넘기지 않습니다.
- API 응답 변환 책임을 도메인이나 JPA 엔티티에 넣지 않습니다.
- 크롤링 대상 사이트별 파싱 규칙은 `crawl.site.<siteCode>` 아래에 둡니다.
- API 요청/응답이 바뀌면 `docs/internal-api.md`도 함께 수정합니다.
- 새 크롤링 정책이나 사이트별 규칙은 `docs/crawling-policy/`에 남깁니다.
- 비밀번호, 토큰, 로컬 접속 정보 같은 민감한 값은 커밋하지 않습니다.
