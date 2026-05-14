# 로켓샐러드 크롤링 정책

검토일: 2026-05-14  
대상 사이트: https://www.rocketsalad.co.kr/  
대상 플랫폼: MakeShop

## 결론

로켓샐러드는 MVP 최우선 수집 대상으로 적합하다. 현재 `robots.txt`는 전체 경로를 허용하고 `/makeshop/`만 차단한다.

상품 목록과 상세가 공개 HTML에 포함되어 있으므로 브라우저 자동화 없이 HTTP 요청과 HTML 파싱으로 수집한다. 다만 사이트 인코딩이 `EUC-KR`이므로 수집기는 응답 본문을 UTF-8로 변환한 뒤 파싱해야 한다.

## 접근 정책

- 허용 기준: `User-agent: *`, `Allow: /`
- 제외 경로: `/makeshop/`
- 권장 시작점: 모바일 페이지
  - 홈 최신 상품: `https://www.rocketsalad.co.kr/m/`
  - 카테고리 목록: `https://www.rocketsalad.co.kr/m/product_list.html?xcode={xcode}&type={type}&viewtype=gallery`
  - 상품 상세: `https://www.rocketsalad.co.kr/m/product.html?branduid={branduid}`
- PC 상세 원본 URL: `https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid={branduid}`

모바일 HTML이 PC HTML보다 상품 카드와 상세 구조가 단순하다. 원본 상품 URL로는 PC 상세 URL을 저장하고, 실제 파싱은 모바일 상세를 우선 사용한다.

## 현재 확인된 값

2026-05-14 현재 홈 최신 상품과 모바일 상세에서 다음 값이 확인된다.

| Vintage Hub 필드       | 현재 로켓샐러드 노출 위치                                                     | 예시                                            | 수집 방식                                                                           |
|----------------------|--------------------------------------------------------------------|-----------------------------------------------|---------------------------------------------------------------------------------|
| `sourceProductId`    | 상세/목록 URL의 `branduid`                                              | `521529`                                      | URL query에서 추출                                                                  |
| `productName`        | 목록 `.pname`, 상세 JSON-LD `name`, `<title>`                          | 90`s Levi`s 550 Relaxed Fit Denim Shorts (33) | 상세 JSON-LD 우선, 없으면 목록/제목을 대체값으로 사용                                              |
| `originalPrice`      | 상세 JSON-LD `offers.price`, 상세 hidden `#price`, 목록 `.price`         | `55000`, `55,000원`                            | 상세 JSON-LD 우선. 할인 구조가 없으면 원가에 저장                                                |
| `salePrice`          | 상세 hidden `#disprice`, 할인판매가 영역                                    | 빈 값                                           | 빈 값이면 `null`                                                                    |
| `stockStatus`        | 상세 JSON-LD `offers.availability`, 상세 `.is_soldout`, 목록 `.sold-out` | `https://schema.org/OutOfStock`, `SOLD OUT`   | JSON-LD 우선. `OutOfStock`/`.is_soldout`/`.sold-out`은 `SOLD_OUT`, 없으면 `AVAILABLE` |
| `description`        | 상세 `#detail_img1` 텍스트                                              | `리바이스의 90년대모델 550 데님 쇼츠 입니다.`                 | HTML 태그 제거 후 공백 정규화                                                             |
| `thumbnailImageUrl`  | 상세 JSON-LD `image[0]`, 목록 `.MS_prod_mobile_image`                  | `/shopimages/yahoochina1/1150020027262.jpg`   | 절대 URL로 정규화                                                                     |
| `productDetailUrl`   | PC 상세 URL                                                          | `/shop/shopdetail.html?branduid=521529`       | `branduid` 기준으로 생성                                                              |
| `sourceCategoryName` | 상세 JSON-LD `category`, 목록 URL `xcode/mcode`, 메뉴명                   | `Pants (대) > casual (중)`                      | 상세 JSON-LD 우선                                                                   |
| `measurements`       | 상세 `#detail_img1` 텍스트                                              | `허리 44cm 허벅지 36cm 밑위 34cm 기장 51cm`            | 정규식 기반 추출                                                                       |

## 목록 수집

목록은 모바일 카테고리 페이지를 기준으로 수집한다.

- 상품 카드 링크: `/m/product.html?branduid=...`
- 상품명: `.pname`
- 보조 설명: `.psubname`
- 가격: `.price` 또는 `.price-info`
- 대표 이미지: `.MS_prod_mobile_image`
- 품절 단서: 상품 카드 내부 `.sold-out`
- 페이징: `page` query 사용

카테고리 페이지는 `sort=order` 최신순을 기본으로 사용한다. `sort=price`, `sort=price2`, `sort=brandname`, `sort=review`는 서비스 수집 목적에 필요 없으므로 수집하지 않는다.

확인된 주요 카테고리 시작점은 다음과 같다.

| 원본 대분류 | URL                                                      | 표준 대분류 후보    |
|--------|----------------------------------------------------------|--------------|
| TOP    | `/m/product_list.html?xcode=113&type=X&viewtype=gallery` | `상의`         |
| OUTER  | `/m/product_list.html?xcode=118&type=X&viewtype=gallery` | `아우터`        |
| PANTS  | `/m/product_list.html?xcode=115&type=X&viewtype=gallery` | `하의`         |
| ACC    | `/m/product_list.html?xcode=116&type=X&viewtype=gallery` | `액세서리`       |
| WOMEN  | `/m/product_list.html?xcode=094&type=X&viewtype=gallery` | 상품명/상세 기반 분류 |

브랜드, 시즌, 세일 카테고리는 중복 상품을 많이 포함한다. MVP에서는 TOP/OUTER/PANTS/ACC/WOMEN 같은 물리 카테고리를 우선 수집하고, 브랜드/시즌 카테고리는 중복 제거 로직이 안정된 뒤 확장한다.

## 상세 수집

상세 수집은 다음 우선순위를 따른다.

1. `script[type="application/ld+json"]`의 `Product` 객체를 파싱한다.
2. 부족한 값은 모바일 상세 DOM에서 보강한다.
3. 상세 설명과 실측은 `#detail_img1` 텍스트를 태그 제거 후 파싱한다.

상세 JSON-LD에서 현재 확인되는 값은 다음과 같다.

- `@id`: PC 상세 URL
- `name`: 상품명
- `description`: 간단 설명
- `image`: 대표 이미지 배열
- `sku`: 내부 상품 코드
- `category`: 원본 카테고리
- `offers.priceCurrency`: `KRW`
- `offers.price`: 숫자 가격
- `offers.availability`: `https://schema.org/InStock` 또는 `https://schema.org/OutOfStock`
- `offers.url`: PC 상세 URL

`branduid`를 원본 상품 식별자로 저장한다. `sku`는 보조 식별자로만 저장하거나 로그에 남긴다. 동일 상품 중복 제거는 `site + branduid` 기준으로 한다.

## 실측 파싱 정책

실측은 상세 본문에 자유 텍스트로 들어간다. 다음 패턴을 우선 추출한다.

- 상의/아우터: `가슴`, `어깨`, `팔길이`, `소매`, `기장`
- 하의: `허리`, `허벅지`, `밑위`, `밑단`, `기장`, `총장`
- 액세서리: `Size: Free`처럼 cm 값이 없으면 실측을 만들지 않고 상세 설명에만 둔다.

추출 규칙은 보수적으로 둔다.

- `허리 44cm 허벅지 36cm 밑위 34cm 기장 51cm`처럼 `항목명 + 숫자 + cm` 패턴만 자동 실측으로 저장한다.
- `국내 33 인치 정도 권장`, `(100)`, `(105,110)` 같은 추천 사이즈나 제목의 괄호 숫자는 실측으로 저장하지 않는다.
- 같은 항목이 여러 번 나오면 상세 본문 하단의 `Size:` 이후에 가까운 값을 우선한다.
- 자동 추출 신뢰도가 낮으면 `ProductMeasurement`를 만들지 않고 운영자 보정 대상으로 남긴다.

## 품절 갱신 정책

품절 판단은 상세 JSON-LD를 최우선으로 한다.

- `offers.availability == https://schema.org/InStock`: `AVAILABLE`
- `offers.availability == https://schema.org/OutOfStock`: `SOLD_OUT`
- JSON-LD가 없고 상세에 `.is_soldout` 또는 `SOLD OUT`이 있으면 `SOLD_OUT`
- 목록 카드에 `.sold-out`이 있으면 목록 단계에서 임시 `SOLD_OUT`으로 저장하되, 상세 수집 결과로 확정한다.
- 요청 실패, 파싱 실패, 또는 판단 단서 부재 시 `UNKNOWN` 또는 `CHECK_FAILED`를 사용한다.

상품 상세 진입 시 백그라운드 재확인은 PC 상세보다 모바일 상세를 먼저 요청한다. 이미 `SOLD_OUT`으로 확정된 상품은 기획 문서의 24시간 TTL을 따른다.

## 변경 감지

매 실행마다 전체 상세를 다시 수집하지 않는다.

1. 홈 최신 상품 또는 주요 카테고리 1페이지의 HTML을 가져온다.
2. `branduid`, 상품명, 가격, 품절 단서, 이미지 URL을 정규화해 지문(fingerprint)을 만든다.
3. 지문이 바뀌면 신규/변경 상품 상세만 수집한다.
4. 기존 상품은 품절 TTL 정책에 따라 상세를 재확인한다.

리뉴얼 의심 조건은 다음과 같다.

- 주요 목록에서 `branduid` 링크가 0개로 떨어짐
- 상세 JSON-LD `Product`가 사라짐
- 상세에서 가격 또는 품절 상태를 30% 이상 파싱하지 못함
- 기존 200 응답이 반복적으로 `403`, `404`, `429`, `5xx`로 바뀜

## 운영 권장값

- `crawlIntervalMinutes`: 60
- 요청 간격: 2-5초
- 병렬 요청: 초기에는 1개
- 인코딩: `EUC-KR` 응답을 UTF-8로 변환
- User-Agent: 서비스명과 연락처를 포함한 식별 가능한 값 사용
- 이미지: URL만 저장하고 원본 이미지는 반복 다운로드하지 않음

로켓샐러드는 신상품 반영 우선순위가 높으므로 운영 안정성이 확인되면 30분 주기로 줄일 수 있다. 단, `429` 또는 응답 지연이 보이면 즉시 3시간 이상으로 완화한다.
