# Vintage Hub 내부 API 명세서

이 문서는 현재 백엔드 코드에 존재하는 API를 기준으로 작성한 내부 명세서이다.

- Base URL: 환경별 호스트 기준
  - local 기준 http://localhost:8080
- Content-Type: `application/json`
- 날짜/시간: ISO-8601 UTC 문자열, 예: `2026-05-15T01:00:00Z`
- 금액/실측값: JSON number로 응답되며 Java 타입은 `BigDecimal`
- 공통 응답 래퍼: 애플리케이션 API 응답은 `ApiResponse<T>` 형식을 사용한다. 단, Spring Boot Actuator 운영 엔드포인트는 Actuator 기본 응답 형식을 사용한다.

## 공통 응답 형식

### 성공 응답

```json
{
  "success": true,
  "data": {}
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `success` | boolean | Y | 요청 성공 여부. 성공 시 `true` |
| `data` | object | Y | API별 응답 데이터 |
| `error` | object | N | 성공 응답에서는 포함되지 않음 |

### 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "ERROR_001",
    "description": "잘못된 요청입니다.",
    "message": "Unsupported crawler site code: unknown"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `success` | boolean | Y | 요청 성공 여부. 오류 시 `false` |
| `data` | object | N | 오류 응답에서는 포함되지 않음 |
| `error.code` | string | Y | 내부 오류 코드 |
| `error.description` | string | Y | 오류 코드 설명 |
| `error.message` | string | Y | 상세 오류 메시지 |

### 공통 오류 코드

| HTTP 상태 | 코드 | 설명 | 발생 조건 |
| --- | --- | --- | --- |
| 400 | `ERROR_001` | 잘못된 요청입니다. | `IllegalArgumentException` 발생 |
| 404 | `ERROR_002` | 요청한 리소스를 찾을 수 없습니다. | `ResourceNotFoundException` 발생 |
| 401 | `ERROR_003` | 인증이 필요합니다. | 관리자 로그인 실패, 관리자 JWT 누락/만료/검증 실패 |
| 403 | `ERROR_004` | 권한이 부족합니다. | 권한 부족 또는 비밀번호 해시 생성 API 비활성화 |
| 500 | `ERROR_999` | 서버 내부 오류입니다. | 처리되지 않은 예외 발생 |

## 운영 헬스 체크

Spring Boot Actuator 헬스 엔드포인트로 애플리케이션 상태를 조회한다.

### 요청

```http
GET /actuator/health
```

### 성공 응답

- HTTP Status: `200 OK`
- 인증: 불필요
- 응답 형식: Spring Boot Actuator 기본 형식

```json
{
  "status": "UP"
}
```

## 관리자 인증

관리자 API는 JWT Bearer 인증을 사용한다.

- 인증 헤더: `Authorization: Bearer {accessToken}`
- 보호 대상: `/api/admin/**`
- 공개 예외: `POST /api/admin/auth/login`, `POST /api/admin/auth/password-hash`
- 관리자 계정은 회원가입 API로 생성하지 않고 `admin_user` 테이블에 사전 발급한다.
- 비밀번호 해시 생성 API는 `VINTAGE_HUB_ADMIN_PASSWORD_HASH_API_ENABLED=true`일 때만 사용한다.

### 관리자 계정 테이블

관리자 계정은 `admin_user` 테이블에 저장한다. `password_hash`에는 비밀번호 원문이 아니라 `POST /api/admin/auth/password-hash`로 생성한 해시 값을 저장한다.

| 컬럼 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | bigint | Y | 관리자 계정 ID |
| `username` | varchar(100) | Y | 관리자 로그인 ID. 유니크 |
| `password_hash` | varchar(255) | Y | Spring Security PasswordEncoder 해시 값 |
| `enabled` | boolean | Y | 로그인 가능 여부. `false`면 로그인 실패 |
| `created_at` | timestamptz | Y | 생성 시각 |
| `updated_at` | timestamptz | Y | 수정 시각 |

예시:

```sql
insert into admin_user (username, password_hash, enabled, created_at, updated_at)
values ('admin', '{bcrypt}$2a$10$...', true, now(), now());
```

### 관리자 인증 설정

| 설정 | 환경 변수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `vintage-hub.admin.password-hash-api-enabled` | `VINTAGE_HUB_ADMIN_PASSWORD_HASH_API_ENABLED` | `false` | 비밀번호 해시 생성 API 활성화 여부 |
| `vintage-hub.jwt.secret` | `VINTAGE_HUB_JWT_SECRET` | 빈 값 | JWT HS256 서명 키 |
| `vintage-hub.jwt.access-token-validity-seconds` | `VINTAGE_HUB_JWT_ACCESS_TOKEN_VALIDITY_SECONDS` | `1800` | access token 유효 시간(초) |

## 1. 관리자 로그인

관리자 계정으로 로그인하고 관리자 API 호출에 사용할 JWT access token을 발급한다.

### 요청

```http
POST /api/admin/auth/login
```

### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `username` | string | Y | 관리자 로그인 ID |
| `password` | string | Y | 관리자 비밀번호 원문 |

### 요청 예시

```json
{
  "username": "admin",
  "password": "admin-secret"
}
```

### 성공 응답

- HTTP Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

### 오류 응답 예시

- HTTP Status: `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "ERROR_003",
    "description": "인증이 필요합니다.",
    "message": "관리자 인증 정보가 올바르지 않습니다."
  }
}
```

## 2. 관리자 비밀번호 해시 생성

관리자 계정 사전 발급을 위해 Spring Security `PasswordEncoder`가 사용하는 비밀번호 해시 값을 생성한다.
운영 배포에서는 기본 비활성화 상태이며, 필요한 환경에서만 `VINTAGE_HUB_ADMIN_PASSWORD_HASH_API_ENABLED=true`로 켠다.

### 요청

```http
POST /api/admin/auth/password-hash
```

### Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `password` | string | Y | 해시로 변환할 비밀번호 원문 |

### 요청 예시

```json
{
  "password": "new-admin-secret"
}
```

### 성공 응답

- HTTP Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "passwordHash": "{bcrypt}$2a$10$..."
  }
}
```

### 오류 응답 예시

- HTTP Status: `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "ERROR_004",
    "description": "권한이 부족합니다.",
    "message": "비밀번호 해시 생성 API가 비활성화되어 있습니다."
  }
}
```

## 3. 상품 목록 조회

상품 목록을 상품명 검색어와 필터 조건으로 조회한다. 정렬 기준을 지정하지 않으면 최신 수집순(`collectedAt` 내림차순)으로 조회한다.

### 요청

```http
GET /api/products
```

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `keyword` | string | N | 없음 | 상품명 검색어. 대소문자를 구분하지 않는 부분 검색이며, 앞뒤 공백은 제거한다. 공백으로 구분된 여러 단어는 모두 상품명에 포함되어야 한다. |
| `siteCode` | string | N | 없음 | 크롤링 사이트 코드. 예: `rocketsalad` |
| `standardCategory` | string | N | 없음 | 표준 대분류. 예: `하의`, `상의`, `아우터`, `액세서리`, `신발` |
| `standardSubCategory` | string | N | 없음 | 표준 중분류. 예: `팬츠`, `셔츠`, `티셔츠`, `니트`, `스웻`, `베스트`, `모자`, `기타`, `벨트`, `쥬얼리`, `넥타이`, `서스펜더` |
| `stockStatus` | string enum | N | 없음 | 재고 상태. `AVAILABLE`, `SOLD_OUT`, `UNKNOWN`, `CHECK_FAILED` |
| `minPrice` | number | N | 없음 | 표시 가격 최솟값. `salePrice`가 있으면 `salePrice`, 없으면 `originalPrice` 기준 |
| `maxPrice` | number | N | 없음 | 표시 가격 최댓값 |
| `measurementFilters` | string[] | N | 없음 | 다중 실측 조건. 같은 이름의 파라미터를 반복 지정하며 `부위:최솟값:최댓값` 형식. 예: `허리:40:50`, `허벅지:30`, `밑단:20:30`. 여러 조건은 모두 만족해야 한다. |
| `measurementPart` | string | N | 없음 | 하위 호환용 단일 실측 부위명. 예: `허리`, `허벅지` |
| `minMeasurement` | number | N | 없음 | 하위 호환용 단일 실측값 최솟값, 단위 cm |
| `maxMeasurement` | number | N | 없음 | 하위 호환용 단일 실측값 최댓값, 단위 cm |
| `sort` | string enum | N | `LATEST` | 정렬 기준. `LATEST`(최신순), `PRICE_LOW`(가격 낮은순), `PRICE_HIGH`(가격 높은순). 가격 정렬은 `displayPrice` 기준 |
| `page` | integer | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | integer | N | `20` | 페이지 크기. 1 미만이면 20으로 보정, 100 초과면 100으로 제한 |

### 요청 예시

```http
GET /api/products?keyword=denim%20pants&siteCode=rocketsalad&standardCategory=하의&standardSubCategory=팬츠&stockStatus=AVAILABLE&minPrice=40000&maxPrice=45000&measurementFilters=허리:40:50&measurementFilters=허벅지:30&measurementFilters=밑단:20:30&sort=PRICE_LOW&page=0&size=10
```

### 성공 응답

- HTTP Status: `200 OK`
- 응답 헤더:
  - `Cache-Control: max-age=30, must-revalidate`
  - `ETag: W/"<hash>"`

동일한 요청 조건의 캐시 검증에는 `If-None-Match` 헤더로 이전 `ETag` 값을 전달할 수 있다. 서버가 같은 결과로 판단하면 `304 Not Modified`를 응답할 수 있으며, 이 경우 응답 본문은 없다.

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "sourceProductId": "matched-pants",
        "name": "조건에 맞는 팬츠",
        "originalPrice": 55000,
        "salePrice": 43000,
        "displayPrice": 43000,
        "detailUrl": "https://example.com/products/matched-pants",
        "thumbnailImageUrl": "https://example.com/images/matched-pants.jpg",
        "siteCode": "rocketsalad",
        "siteName": "로켓샐러드",
        "standardCategory": "하의",
        "standardSubCategory": "팬츠",
        "stockStatus": "AVAILABLE",
        "collectedAt": "2026-05-15T01:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 응답 데이터 타입

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `content` | array | N | 상품 목록 |
| `content[].id` | integer | N | 내부 상품 ID |
| `content[].sourceProductId` | string | N | 원본 사이트 상품 ID |
| `content[].name` | string | N | 상품명 |
| `content[].originalPrice` | number | Y | 원가 |
| `content[].salePrice` | number | Y | 할인가 |
| `content[].displayPrice` | number | Y | 표시 가격. `salePrice` 우선, 없으면 `originalPrice` |
| `content[].detailUrl` | string | N | 원본 상품 상세 URL |
| `content[].thumbnailImageUrl` | string | Y | 썸네일 이미지 URL |
| `content[].siteCode` | string | N | 사이트 코드 |
| `content[].siteName` | string | N | 사이트 표시명 |
| `content[].standardCategory` | string | Y | 표준 대분류 |
| `content[].standardSubCategory` | string | Y | 표준 중분류 |
| `content[].stockStatus` | string enum | N | `AVAILABLE`, `SOLD_OUT`, `UNKNOWN`, `CHECK_FAILED` |
| `content[].collectedAt` | string(datetime) | N | 상품 수집 시각 |
| `page` | integer | N | 현재 페이지 번호 |
| `size` | integer | N | 응답 페이지 크기 |
| `totalElements` | integer | N | 전체 상품 수 |
| `totalPages` | integer | N | 전체 페이지 수 |

### 성능 및 리소스 정책

- 목록 조회는 엔티티 전체를 로딩하지 않고 목록 응답에 필요한 필드만 DTO projection으로 조회한다. `description` 같은 상세 전용 대용량 컬럼은 목록 응답 쿼리에서 제외한다.
- 기본 최신순 조회, 사이트/카테고리/재고 필터, 가격 필터/정렬, 실측 필터, 상품명 부분 검색에 맞춘 PostgreSQL 인덱스를 사용한다.
- 상품명 부분 검색은 `pg_trgm` 기반 GIN 인덱스(`lower(name)`)를 사용하도록 마이그레이션에 포함한다.
- 페이지 크기는 최대 100개로 제한한다. 1 미만이면 20으로 보정한다.

## 4. 상품 필터 옵션 조회

프론트엔드의 상품 검색 필터 구성을 위해 현재 상품 데이터에 실제로 존재하는 사이트, 표준 카테고리, 실측 부위 목록과 상품 목록 정렬 옵션을 조회한다.

- 상품이 1개 이상 있는 사이트만 응답한다.
- `standardCategory`, `standardSubCategory`, 실측 `part`가 `null`이거나 빈 문자열인 값은 제외한다.
- 카테고리는 대분류 기준 상품 수 내림차순, 이름 오름차순으로 정렬한다.
- 사이트는 상품 수 내림차순, 사이트 코드 오름차순으로 정렬한다.
- 실측 부위는 부위명 오름차순으로 정렬한다.
- 정렬 옵션은 기본 정렬인 `LATEST`를 먼저 응답한 뒤 `PRICE_LOW`, `PRICE_HIGH` 순서로 응답한다.

### 요청

```http
GET /api/products/filter-options
```

### 성공 응답

- HTTP Status: `200 OK`
- 응답 헤더:
  - `Cache-Control: max-age=60, must-revalidate`
  - `ETag: W/"<hash>"`

동일한 필터 옵션 응답의 캐시 검증에는 `If-None-Match` 헤더로 이전 `ETag` 값을 전달할 수 있다. 서버가 같은 결과로 판단하면 `304 Not Modified`를 응답할 수 있으며, 이 경우 응답 본문은 없다.

```json
{
  "success": true,
  "data": {
    "sites": [
      {
        "code": "rocketsalad",
        "name": "로켓샐러드",
        "productCount": 123
      }
    ],
    "categories": [
      {
        "name": "하의",
        "productCount": 50,
        "subCategories": [
          {
            "name": "팬츠",
            "productCount": 42
          },
          {
            "name": "데님",
            "productCount": 8
          }
        ]
      },
      {
        "name": "상의",
        "productCount": 40,
        "subCategories": [
          {
            "name": "셔츠",
            "productCount": 12
          },
          {
            "name": "니트",
            "productCount": 8
          }
        ]
      }
    ],
    "measurements": [
      {
        "part": "가슴",
        "productCount": 45
      },
      {
        "part": "어깨",
        "productCount": 31
      },
      {
        "part": "허리",
        "productCount": 28
      }
    ],
    "sorts": [
      {
        "code": "LATEST",
        "name": "최신순"
      },
      {
        "code": "PRICE_LOW",
        "name": "가격 낮은순"
      },
      {
        "code": "PRICE_HIGH",
        "name": "가격 높은순"
      }
    ]
  }
}
```

### 응답 데이터 타입

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `sites` | array | N | 상품이 존재하는 크롤링 사이트 목록 |
| `sites[].code` | string | N | 사이트 코드. 상품 목록 조회의 `siteCode` 값으로 사용 |
| `sites[].name` | string | N | 사이트 표시명 |
| `sites[].productCount` | integer | N | 해당 사이트의 상품 수 |
| `categories` | array | N | 상품이 존재하는 표준 대분류 목록 |
| `categories[].name` | string | N | 표준 대분류명. 상품 목록 조회의 `standardCategory` 값으로 사용 |
| `categories[].productCount` | integer | N | 해당 표준 대분류의 상품 수 |
| `categories[].subCategories` | array | N | 해당 대분류 아래 상품이 존재하는 표준 중분류 목록 |
| `categories[].subCategories[].name` | string | N | 표준 중분류명. 상품 목록 조회의 `standardSubCategory` 값으로 사용 |
| `categories[].subCategories[].productCount` | integer | N | 해당 표준 중분류의 상품 수 |
| `measurements` | array | N | 상품 실측에 실제로 존재하는 부위 목록 |
| `measurements[].part` | string | N | 실측 부위명. `measurementFilters` 또는 `measurementPart` 값으로 사용 |
| `measurements[].productCount` | integer | N | 해당 실측 부위를 가진 상품 수 |
| `sorts` | array | N | 상품 목록 조회에서 사용할 수 있는 정렬 옵션 |
| `sorts[].code` | string enum | N | 상품 목록 조회의 `sort` 값. `LATEST`, `PRICE_LOW`, `PRICE_HIGH` |
| `sorts[].name` | string | N | 화면 표시용 정렬 이름 |

### 성능 및 리소스 정책

- 필터 옵션 조회 결과는 서버 메모리에 짧게 캐시한다. 기본 TTL은 `vintage-hub.product.filter-options.cache-ttl=60s`이다.
- 운영에서는 반복 호출 시 사이트/카테고리/중분류/실측 aggregate 쿼리를 매번 실행하지 않는다.
- 테스트 환경은 데이터 초기화 순서 의존성을 피하기 위해 `vintage-hub.product.filter-options.cache-ttl=0s`로 캐시를 비활성화한다.

## 5. 상품 상세 조회

사이트 코드와 원본 상품 ID로 상품 상세 정보를 조회한다.

### 요청

```http
GET /api/products/{siteCode}/{sourceProductId}
```

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `siteCode` | string | Y | 크롤링 사이트 코드. 예: `rocketsalad` |
| `sourceProductId` | string | Y | 원본 사이트 상품 ID |

### 요청 예시

```http
GET /api/products/rocketsalad/521529
```

### 성공 응답

- HTTP Status: `200 OK`
- 응답 헤더:
  - `Cache-Control: max-age=60, must-revalidate`
  - `ETag: W/"<hash>"`

동일한 상품 상세 응답의 캐시 검증에는 `If-None-Match` 헤더로 이전 `ETag` 값을 전달할 수 있다. 서버가 같은 결과로 판단하면 `304 Not Modified`를 응답할 수 있으며, 이 경우 응답 본문은 없다.

```json
{
  "success": true,
  "data": {
    "id": 1,
    "sourceProductId": "521529",
    "name": "90s Levi's denim shorts",
    "originalPrice": 55000,
    "salePrice": null,
    "displayPrice": 55000,
    "description": "90s Levi's denim shorts 상세 설명",
    "detailUrl": "https://example.com/products/521529",
    "thumbnailImageUrl": "https://example.com/images/521529.jpg",
    "siteCode": "rocketsalad",
    "siteName": "로켓샐러드",
    "standardCategory": "하의",
    "standardSubCategory": "팬츠",
    "stockStatus": "AVAILABLE",
    "collectedAt": "2026-05-15T01:00:00Z",
    "lastSeenAt": "2026-05-15T01:00:00Z",
    "availabilityCheckedAt": "2026-05-15T01:00:00Z",
    "measurements": [
      {
        "part": "허리",
        "valueCm": 44.00,
        "updatedAt": "2026-05-15T01:00:00Z"
      },
      {
        "part": "허벅지",
        "valueCm": 36.00,
        "updatedAt": "2026-05-15T01:00:00Z"
      }
    ]
  }
}
```

### 응답 데이터 타입

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `id` | integer | N | 내부 상품 ID |
| `sourceProductId` | string | N | 원본 사이트 상품 ID |
| `name` | string | N | 상품명 |
| `originalPrice` | number | Y | 원가 |
| `salePrice` | number | Y | 할인가 |
| `displayPrice` | number | Y | 표시 가격. `salePrice` 우선, 없으면 `originalPrice` |
| `description` | string | Y | 상품 상세 설명 |
| `detailUrl` | string | N | 원본 상품 상세 URL |
| `thumbnailImageUrl` | string | Y | 썸네일 이미지 URL |
| `siteCode` | string | N | 사이트 코드 |
| `siteName` | string | N | 사이트 표시명 |
| `standardCategory` | string | Y | 표준 대분류 |
| `standardSubCategory` | string | Y | 표준 중분류 |
| `stockStatus` | string enum | N | `AVAILABLE`, `SOLD_OUT`, `UNKNOWN`, `CHECK_FAILED` |
| `collectedAt` | string(datetime) | N | 최초/최근 수집 시각 |
| `lastSeenAt` | string(datetime) | N | 마지막으로 상품을 확인한 시각 |
| `availabilityCheckedAt` | string(datetime) | Y | 재고 상태 확인 시각 |
| `measurements` | array | N | 실측 정보 목록. 상품 ID 기준 오름차순으로 저장된 실측 ID 순서 |
| `measurements[].part` | string | N | 실측 부위명 |
| `measurements[].valueCm` | number | N | 실측값, 단위 cm |
| `measurements[].updatedAt` | string(datetime) | N | 실측 정보 갱신 시각 |

### 성능 및 리소스 정책

- 상품 본문 조회 시 사이트 정보를 fetch join으로 함께 조회해 상세 응답 생성 과정의 추가 지연 로딩을 줄인다.
- 실측 정보는 `product_measurement(product_id, id)` 인덱스를 사용해 상품별 실측 목록을 안정적으로 조회한다.

### 오류 응답 예시

- HTTP Status: `404 Not Found`

```json
{
  "success": false,
  "error": {
    "code": "ERROR_002",
    "description": "요청한 리소스를 찾을 수 없습니다.",
    "message": "상품을 찾을 수 없습니다. siteCode=rocketsalad, sourceProductId=unknown"
  }
}
```

## 6. 관리자 수동 크롤 실행 요청

지정한 사이트의 수동 크롤을 실행한다. 현재 구현은 비동기 작업 ID를 반환하지 않고, 서비스가 크롤 수행 후 집계 결과를 `202 Accepted`로 반환한다.
JWT Bearer 인증이 필요하다.

### 요청

```http
POST /api/admin/crawl-sites/{siteCode}/crawl-runs
Authorization: Bearer {accessToken}
```

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `siteCode` | string | Y | 크롤링 사이트 코드. 예: `rocketsalad` |

### Request Body

없음.

### 요청 예시

```http
POST /api/admin/crawl-sites/rocketsalad/crawl-runs
Authorization: Bearer {accessToken}
```

### 성공 응답

- HTTP Status: `202 Accepted`

```json
{
  "success": true,
  "data": {
    "siteCode": "rocketsalad",
    "status": "SUCCEEDED",
    "foundCount": 2,
    "createdCount": 1,
    "updatedCount": 1,
    "failedCount": 0,
    "message": "Crawl run completed."
  }
}
```

### 응답 데이터 타입

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `siteCode` | string | N | 크롤링 사이트 코드 |
| `status` | string enum | N | 크롤 실행 상태. 현재 응답 예: `SUCCEEDED`, 실패 시 내부 실행 기록은 `FAILED` |
| `foundCount` | integer | N | 크롤 목록에서 발견한 상품 수 |
| `createdCount` | integer | N | 신규 생성된 상품 수 |
| `updatedCount` | integer | N | 기존 상품 중 갱신된 상품 수 |
| `failedCount` | integer | N | 개별 상품 처리 실패 수 |
| `message` | string | Y | 실행 결과 메시지. 일부 상품 실패 시 실패 사유가 포함될 수 있음 |

### 오류 응답 예시

- HTTP Status: `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "ERROR_001",
    "description": "잘못된 요청입니다.",
    "message": "Unsupported crawler site code: unknown"
  }
}
```

## 7. 관리자 상품 품절 여부 수동 확인

지정한 상품의 원본 상세 페이지를 다시 확인하고 `stockStatus`, `availabilityCheckedAt`, 다음 확인 예정 시각을 갱신한다.
JWT Bearer 인증이 필요하다.

### 요청

```http
POST /api/admin/products/{productId}/availability-check
Authorization: Bearer {accessToken}
```

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `productId` | integer | Y | 내부 상품 ID |

### Request Body

없음.

### 요청 예시

```http
POST /api/admin/products/1/availability-check
Authorization: Bearer {accessToken}
```

### 성공 응답

- HTTP Status: `202 Accepted`

```json
{
  "success": true,
  "data": {
    "checkedCount": 1,
    "availableCount": 1,
    "soldOutCount": 0,
    "unknownCount": 0,
    "failedCount": 0
  }
}
```

### 응답 데이터 타입

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `checkedCount` | integer | N | 확인을 시도한 상품 수. 단일 상품 API에서는 보통 `1` |
| `availableCount` | integer | N | 확인 결과 `AVAILABLE`인 상품 수 |
| `soldOutCount` | integer | N | 확인 결과 `SOLD_OUT`인 상품 수 |
| `unknownCount` | integer | N | 확인 결과 `UNKNOWN`인 상품 수 |
| `failedCount` | integer | N | 확인 실패 또는 `CHECK_FAILED` 결과 상품 수 |

### 오류 응답 예시

- HTTP Status: `404 Not Found`

```json
{
  "success": false,
  "error": {
    "code": "ERROR_002",
    "description": "요청한 리소스를 찾을 수 없습니다.",
    "message": "Product not found: 99"
  }
}
```

## 상품 품절 여부 배치 설정

상품 품절 여부 배치는 `availability_next_check_at <= now()`인 상품이 있는 사이트를 조회한 뒤, 사이트별로 오래된 확인 예정 시각순 `batch-size`개를 처리한다.
대량의 `AVAILABLE`, `SOLD_OUT` 상품이 쌓여도 전체 상품을 매번 순회하지 않고, 확인 예정 시각이 도래한 상품만 처리한다.
여러 사이트에 확인 대상 상품이 있으면 사이트 단위 작업을 병렬 실행하되, 동시에 실행되는 사이트 수는 `max-parallel-sites`로 제한한다.

| 설정                                                        | 기본값    | 설명                                    |
|-----------------------------------------------------------|--------|---------------------------------------|
| `vintage-hub.product.availability-check.enabled`          | `true` | 품절 여부 배치 활성화 여부                       |
| `vintage-hub.product.availability-check.fixed-rate`       | `10m`  | 배치 실행 주기                              |
| `vintage-hub.product.availability-check.batch-size`       | `20`   | 1회 배치에서 사이트별로 확인할 최대 상품 수             |
| `vintage-hub.product.availability-check.max-parallel-sites` | `3`    | 동시에 품절 확인을 실행할 최대 사이트 수                |
| `vintage-hub.product.availability-check.request-delay`    | `1s`   | 상품별 원본 사이트 요청 간격                      |
| `vintage-hub.product.availability-check.available-ttl`    | `6h`   | `AVAILABLE` 확인 후 다음 확인까지의 간격          |
| `vintage-hub.product.availability-check.sold-out-ttl`     | `7d`   | `SOLD_OUT` 확인 후 다음 확인까지의 간격           |
| `vintage-hub.product.availability-check.unknown-ttl`      | `1h`   | `UNKNOWN` 확인 후 다음 확인까지의 간격            |
| `vintage-hub.product.availability-check.check-failed-ttl` | `2h`   | 실패 또는 `CHECK_FAILED` 확인 후 다음 확인까지의 간격 |

## 참고 사항

- 현재 OpenAPI JSON은 `GET /v3/api-docs`에서 제공된다.
- Swagger UI 의존성은 포함되어 있으므로 일반적인 Springdoc 기본 경로(`/swagger-ui.html` 또는 `/swagger-ui/index.html`)에서 확인할 수 있다.
- `ProductDetailResult` 내부에는 `categoryConfidence`, 실측 `rawText`, `confidence`, `source`가 존재하지만 현재 상품 상세 API 응답 DTO에는 포함되지 않는다.
