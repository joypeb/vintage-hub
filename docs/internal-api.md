# Vintage Hub 내부 API 명세서

이 문서는 현재 백엔드 코드에 존재하는 API를 기준으로 작성한 내부 명세서이다.

- Base URL: 환경별 호스트 기준
- Content-Type: `application/json`
- 날짜/시간: ISO-8601 UTC 문자열, 예: `2026-05-15T01:00:00Z`
- 금액/실측값: JSON number로 응답되며 Java 타입은 `BigDecimal`
- 공통 응답 래퍼: 모든 API 응답은 `ApiResponse<T>` 형식을 사용한다.

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

상품 목록을 최신 수집순(`collectedAt` 내림차순)으로 조회한다.

### 요청

```http
GET /api/products
```

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `siteCode` | string | N | 없음 | 크롤링 사이트 코드. 예: `rocketsalad` |
| `standardCategory` | string | N | 없음 | 표준 대분류. 예: `하의`, `상의`, `아우터`, `액세서리`, `신발` |
| `standardSubCategory` | string | N | 없음 | 표준 중분류. 예: `팬츠`, `셔츠`, `티셔츠`, `니트`, `스웻`, `베스트`, `모자`, `기타`, `벨트`, `쥬얼리`, `넥타이`, `서스펜더` |
| `stockStatus` | string enum | N | 없음 | 재고 상태. `AVAILABLE`, `SOLD_OUT`, `UNKNOWN`, `CHECK_FAILED` |
| `minPrice` | number | N | 없음 | 표시 가격 최솟값. `salePrice`가 있으면 `salePrice`, 없으면 `originalPrice` 기준 |
| `maxPrice` | number | N | 없음 | 표시 가격 최댓값 |
| `measurementPart` | string | N | 없음 | 실측 부위명. 예: `허리`, `허벅지` |
| `minMeasurement` | number | N | 없음 | 실측값 최솟값, 단위 cm |
| `maxMeasurement` | number | N | 없음 | 실측값 최댓값, 단위 cm |
| `page` | integer | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | integer | N | `20` | 페이지 크기. 1 미만이면 20으로 보정, 100 초과면 100으로 제한 |

### 요청 예시

```http
GET /api/products?siteCode=rocketsalad&standardCategory=하의&standardSubCategory=팬츠&stockStatus=AVAILABLE&minPrice=40000&maxPrice=45000&measurementPart=허리&minMeasurement=40&maxMeasurement=44&page=0&size=10
```

### 성공 응답

- HTTP Status: `200 OK`

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

## 4. 상품 상세 조회

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

## 5. 관리자 수동 크롤 실행 요청

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

## 참고 사항

- 현재 OpenAPI JSON은 `GET /v3/api-docs`에서 제공된다.
- Swagger UI 의존성은 포함되어 있으므로 일반적인 Springdoc 기본 경로(`/swagger-ui.html` 또는 `/swagger-ui/index.html`)에서 확인할 수 있다.
- `ProductDetailResult` 내부에는 `categoryConfidence`, 실측 `rawText`, `confidence`, `source`가 존재하지만 현재 상품 상세 API 응답 DTO에는 포함되지 않는다.
