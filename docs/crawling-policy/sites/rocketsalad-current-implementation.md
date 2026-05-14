# 로켓샐러드 현재 크롤링 구현

작성일: 2026-05-14  
대상 코드:

- `src/main/java/com/joypeb/vintagehub/crawl/site/rocketsalad/RocketSaladCrawler.java`
- `src/main/java/com/joypeb/vintagehub/crawl/site/rocketsalad/HttpRocketSaladPageClient.java`
- `src/main/java/com/joypeb/vintagehub/crawl/application/CrawlRunService.java`

## 전체 흐름

현재 로켓샐러드 크롤러는 `SiteCrawler` 구현체인 `RocketSaladCrawler`로 등록된다. 사이트 코드는 `rocketsalad`이다.

관리자 API가 `CrawlRunService.requestManualRun(siteCode)`를 호출하면 다음 순서로 실행된다.

1. `crawl_site.code`로 사이트 설정을 조회한다.
2. `CrawlerRegistry`에서 `rocketsalad` 크롤러를 찾는다.
3. `crawl_run`을 `MANUAL` 실행으로 생성하고 `RUNNING` 상태로 바꾼다.
4. 크롤러가 제공하는 초기 커서를 기준으로 카테고리별 목록 수집을 시작한다.
5. 각 목록의 `nextCursor`를 따라 다음 페이지를 수집하되, 초기 커서당 최대 3페이지만 수집한다.
6. 목록에서 찾은 상품이 이미 DB에 있으면 해당 카테고리 수집을 멈춘다.
7. 신규 상품이면 `fetchDetail(site, summary.ref())`를 호출해 상품을 저장한다.
8. `site + sourceProductId` 기준으로 기존 상품을 업데이트하지 않고 중복 지점에서 중단한다.
9. 상세에서 추출한 실측은 기존 실측을 삭제한 뒤 새로 저장한다.
10. 전체 실행 결과를 `SUCCEEDED` 또는 `FAILED`로 기록한다.

상품 하나의 상세 수집이 실패해도 전체 실행은 중단하지 않는다. 해당 상품은 `failedCount`가 증가하고 실패 원인은 `crawl_run.message`에 요약된다. 반면 목록 수집이나 실행 전체에서 `RuntimeException`이 발생하면 실행 상태를 `FAILED`로 기록한 뒤 예외를 다시 던진다.

## HTTP 요청

`HttpRocketSaladPageClient`는 Java `HttpClient`로 HTML을 가져온다.

- 연결 타임아웃: 10초
- 요청 타임아웃: 20초
- HTTP 버전: HTTP/1.1
- 리다이렉트: Java `HttpClient` 자동 추적을 끄고 최대 5회까지 수동 추적
- `User-Agent`: `VintageHubCrawler/0.1 (+https://vintage-hub.local)`
- `Accept`: `text/html,application/xhtml+xml`
- 응답 디코딩: `EUC-KR`

HTTP 상태 코드가 `2xx`가 아니면 `IllegalStateException`을 던진다. `301`, `302`, `303`, `307`, `308`은 `Location` 헤더를 현재 URI 기준으로 해석해 직접 따라간다. I/O 오류와 인터럽트도 `IllegalStateException`으로 래핑된다.

현재 구현에는 요청 간 지연, 재시도, 레이트 리밋 대응, 병렬 수집 제한 로직이 없다.

## 목록 수집

목록 수집은 모바일 상품 목록 페이지를 사용한다.

커서는 `카테고리:페이지` 형식이다. 요청 커서가 없거나 비어 있으면 크롤러 내부 기본값인 `PANTS:1`을 사용한다. 형식이 잘못되면 `PANTS:1`로 되돌아가고, 페이지 숫자만 잘못되면 해당 카테고리의 1페이지를 사용한다.

현재 코드에 들어 있는 카테고리 매핑은 다음과 같다.

| 카테고리 | xcode |
|---|---:|
| `TOP` | `113` |
| `OUTER` | `118` |
| `PANTS` | `115` |
| `ACC` | `116` |
| `WOMEN` | `094` |

수동 실행의 초기 커서는 다음 순서로 제공된다.

1. `TOP:1`
2. `OUTER:1`
3. `PANTS:1`
4. `ACC:1`
5. `WOMEN:1`

목록 URL은 다음 형식으로 생성한다.

```text
https://www.rocketsalad.co.kr/m/product_list.html?xcode={xcode}&type=X&viewtype=gallery&page={page}&sort=order
```

목록 HTML은 Jsoup으로 파싱하며, 다음 셀렉터를 사용한다.

| 값 | 추출 방식 |
|---|---|
| 상품 링크 | `a[href*=/m/product.html?branduid=]` |
| `sourceProductId` | 링크 URL의 `branduid` 쿼리 |
| 상품명 | 링크 내부 `.pname` 텍스트, 없으면 `title` 속성 |
| 대표 이미지 | 링크 내부 `.MS_prod_mobile_image`의 `src` |
| 원본 상세 URL | `branduid`로 PC 상세 URL 생성 |

목록 결과는 Java `distinct()`로 중복 제거한다. 다음 커서는 항상 같은 카테고리의 다음 페이지로 반환한다.

예를 들어 `PANTS:2`를 수집하면 다음 커서는 `PANTS:3`이다.

수동 실행 서비스는 각 초기 커서에서 시작해 `nextCursor`가 `null`이거나, 목록이 비어 있거나, 이미 DB에 저장된 상품을 만날 때까지 다음 페이지를 호출한다. 빈 DB 최초 수집처럼 중복 상품을 만나지 못하는 경우를 대비해 초기 커서당 최대 3페이지만 수집한다. 이미 저장된 상품을 만나면 그 상품은 업데이트하지 않고 현재 카테고리 페이징을 중단한다. 이는 최신순 목록에서 이전 실행 때 이미 수집한 구간을 다시 따라가며 무한히 수집하는 것을 막기 위한 동작이다.

## 상세 수집

상세 수집은 모바일 상세 페이지를 사용한다.

```text
https://www.rocketsalad.co.kr/m/product.html?branduid={sourceProductId}
```

단, 상품에 저장하는 원본 상세 URL은 PC 상세 URL이다.

```text
https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid={sourceProductId}
```

상세 HTML에서는 먼저 `script[type=application/ld+json]`를 순회하면서 JSON-LD의 `Product` 객체를 찾는다. JSON-LD가 배열이거나 `@graph` 안에 있어도 재귀적으로 탐색한다. JSON 파싱 중 예외가 발생하면 상품 JSON은 없는 것으로 처리한다.

상세 필드 추출 규칙은 다음과 같다.

| 저장 값 | 현재 추출 우선순위 |
|---|---|
| 상품명 | JSON-LD `name`, 없으면 문서 `title` |
| 원가 | JSON-LD `offers.price`, 없으면 `#price` input value |
| 할인가 | `#disprice` input value |
| 품절 상태 | JSON-LD `offers.availability`, 없으면 DOM 품절 단서 |
| 설명 | `#detail_img1` 텍스트 |
| 대표 이미지 | JSON-LD `image[0]` 또는 `image`, 없으면 `.MS_prod_mobile_image` `src` |
| 원본 카테고리 | JSON-LD `category` |
| 실측 | 설명 텍스트에서 정규식 추출 |

가격은 숫자와 점을 제외한 문자를 제거한 뒤 `BigDecimal`로 변환한다. 값이 비어 있으면 `null`이다.

텍스트 값은 연속 공백을 하나의 공백으로 줄이고 앞뒤 공백을 제거한다.

## 품절 상태 판단

품절 상태는 다음 순서로 판단한다.

1. JSON-LD `offers.availability`가 `/InStock`으로 끝나면 `AVAILABLE`
2. JSON-LD `offers.availability`가 `/OutOfStock`으로 끝나면 `SOLD_OUT`
3. 상세 DOM에 `.is_soldout` 또는 `.sold-out`이 있으면 `SOLD_OUT`
4. 상세 전체 텍스트에 `SOLD OUT`이 있으면 `SOLD_OUT`
5. 위 단서가 없으면 `UNKNOWN`

목록 카드의 품절 표시는 현재 요약 데이터에 저장하지 않고, 상세 수집 결과로만 최종 저장한다. 품절 상품도 필터링하지 않고 저장하며, `stockStatus`에 `SOLD_OUT` 또는 `UNKNOWN`을 반영한다.

`checkAvailability()`는 별도 경량 요청을 하지 않고 `fetchDetail()`을 호출한 뒤 상세 결과의 `availability`만 반환한다.

## 실측 추출

실측은 `#detail_img1` 텍스트에서 다음 정규식으로 추출한다.

```text
(가슴|어깨|팔길이|소매|기장|총장|허리|허벅지|밑위|밑단)\s*(\d+(?:\.\d+)?)\s*cm
```

현재 자동 추출 대상 항목은 다음이다.

- `가슴`
- `어깨`
- `팔길이`
- `소매`
- `기장`
- `총장`
- `허리`
- `허벅지`
- `밑위`
- `밑단`

같은 항목이 여러 번 나오면 뒤에서 발견된 값이 앞의 값을 덮어쓴다. 추출된 값은 문자열로 상세 결과에 담기고, 저장 시 `BigDecimal` 센티미터 값으로 `product_measurement`에 저장된다. 실측의 출처 텍스트는 상세 설명 전체를 사용한다.

## 저장 로직

신규 상품은 `ProductEntity.updateFrom(detail, summary, collectedAt)`로 저장한다. 이미 존재하는 상품을 만나면 상세를 다시 수집하거나 업데이트하지 않고 현재 카테고리 수집을 멈춘다.

현재 저장되는 주요 값은 다음과 같다.

| 컬럼/값 | 저장 방식 |
|---|---|
| `sourceProductId` | `branduid` |
| `name` | 상세 상품명 우선, 없으면 목록 상품명 |
| `originalPrice` | 상세 원가 |
| `salePrice` | 상세 할인가 |
| `stockStatus` | 상세 품절 상태 |
| `description` | 상세 설명 |
| `detailUrl` | PC 상세 URL |
| `thumbnailImageUrl` | 상세 대표 이미지 URL |
| `sourceCategoryName` | JSON-LD 카테고리 |
| `collectedAt` | 현재 실행 시각 |
| `lastSeenAt` | 현재 실행 시각 |
| `availabilityCheckedAt` | 현재 실행 시각 |

`standardCategory`, `standardSubCategory`, `categoryConfidence`는 저장 시 `null`로 초기화된다.

실측은 상품 저장 후 `deleteByProduct(product)`로 기존 값을 모두 지우고, 이번 상세에서 추출된 값만 다시 저장한다.

이미지는 파일로 저장하지 않는다. `thumbnailImageUrl`에 원본 이미지 URL만 저장한다.

## 실패 기록

상품 상세 수집이나 상품 저장 중 `RuntimeException`이 발생하면 해당 상품은 건너뛰고 다음 상품 수집을 계속한다.

- `failedCount`를 1 증가시킨다.
- `sourceProductId: 실패 메시지` 형식의 실패 원인을 모은다.
- 실행 완료 시 실패 원인이 있으면 `crawl_run.message`에 `Crawl run completed with failures: ...` 형식으로 저장한다.
- `crawl_run.message` 컬럼 길이에 맞춰 메시지는 최대 1000자로 자른다.

## 현재 구현의 한계

- `nextCursor` 자체를 DB에 영속화하지는 않는다. 실행 중 메모리에서만 다음 페이지를 따라간다.
- 초기 커서당 수집 페이지 수는 공통 상수로 제한하며 현재 값은 3페이지다.
- 목록에서 가격이나 품절 상태를 저장하지 않는다.
- 요청 간 지연, 재시도, 429 대응은 없다.
- 실패 원인은 `crawl_run.message`에 요약되며, 별도 상품별 실패 테이블은 없다.
- 변경 감지나 fingerprint 기반 상세 수집 생략 로직은 아직 없다.
- 이미지 파일은 다운로드하지 않고 URL만 저장한다.
