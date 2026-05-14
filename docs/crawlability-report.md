# 빈티지 쇼핑몰 크롤링 가능성 검토

검토일: 2026-05-13  
검토 기준: `robots.txt`, 대상 URL HTTP 응답, 원본 HTML 내 상품 목록/상품 링크 존재 여부  
검토 범위: 공개 상품/목록 페이지의 기술적 수집 가능성. 사이트 이용약관/제휴 계약까지 확인한 법률 검토는 아님.

## 요약

- 기술적으로 크롤링 가능: 12개
- 조건부 가능: 1개, `m.bunjang.co.kr/shops/1545488/products`
- 크롤링 비권장/불가: 1개, `smartstore.naver.com/terracewear`
- 대부분 Cafe24/MakeShop/Imweb 기반 쇼핑몰이며, 공개 HTML에 상품명, 가격, 이미지, 상세 링크가 포함되어 있어 브라우저 자동화 없이도 수집 가능하다.
- 네이버 스마트스토어는 `robots.txt`에서 `User-agent: *`에 대해 전체 경로를 차단하고, 대상 URL 요청도 로그인 리다이렉트 또는 `429` 제한 응답을 보여 자동 수집 대상으로 부적합하다.
- 번개장터 테라스웨어는 `robots.txt`상 허용이고 웹 API에서 판매자 상품 JSON이 확인되지만, 페이지 HTML 자체에는 상품 데이터가 없어 API/브라우저 렌더링 기반 수집이 필요하다.

## 판정 기준

| 판정 | 의미 |
|---|---|
| 가능 | `robots.txt`상 대상 경로가 막히지 않고, HTTP 200으로 접근되며, 원본 HTML에서 상품 데이터 단서가 확인됨 |
| 조건부 가능 | 기본 수집은 가능하지만 페이징, 정렬, API, 요청 간격, 약관 확인이 특히 필요함 |
| 비권장/불가 | `robots.txt` 차단, 로그인/429/차단 응답, 플랫폼 정책상 자동 수집 리스크가 큼 |

## 전체 결과

| 사이트 | 대상 URL 응답 | `robots.txt` 기준 | HTML 내 상품 단서 | 판정 | 주요 메모 |
|---|---:|---|---|---|---|
| 로켓샐러드 | 200 | `Allow: /`, `/makeshop/`만 차단 | `/shop/shopdetail.html` 링크 다수 | 가능 | MakeShop 계열. 목록/상세 URL은 차단 경로가 아님 |
| 도쿄유즈드 | 200 | 관리자/장바구니/검색/정렬 등 차단, 상품/목록 경로는 허용 | Cafe24 `prdList`, 상품명/판매가/상세 링크 확인 | 가능 | `sitemap.xml` 제공. 정렬/필터 URL은 피해야 함 |
| 헬로스트레인저 | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | Cafe24 상품 목록/판매가 확인 | 가능 | 일부 봇에 `Crawl-delay: 10`이 있으므로 느린 수집 권장 |
| 보노비스타 | 200 | 관리자/장바구니/검색/정렬 등 차단, 상품/목록 경로는 허용 | Cafe24 `prdList`, 상품명/가격/상세 링크 확인 | 가능 | `sitemap.xml` 제공. 정렬/필터 URL은 제외 |
| 컨트리보이즈 | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | 상품 상세 링크 매우 많음 | 가능 | 대상 카테고리 HTML 크기가 커서 페이지 단위 캐싱 권장 |
| 페스티나렌테 | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | Cafe24 `prdList`, 상품 상세 링크 확인 | 가능 | 일부 봇에 `Crawl-delay: 10` |
| 마일드컨트리 | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | 상품명/판매가/상세 링크 확인 | 가능 | 홈에 상품 목록이 포함됨 |
| 1983빈티지 | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | 상품 상세 링크 확인 | 가능 | 대상 카테고리 경로 수집 가능 |
| PAFJ | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | 상품 구매/상세 데이터 다수 확인 | 가능 | 상품 수가 많아 페이징/중복 제거 필요 |
| 소버서울 | 200 | `Allow: /`, 로그인/장바구니/admin 등만 차단 | Imweb 상품 링크 `/ALL/?idx=...`, 상품 수/목록 확인 | 가능 | `sitemap.xml` 제공. `more` 페이징은 요청 간격 조절 필요 |
| 매그놀리아미스 | 200 | Cafe24 공통 차단 외 상품/목록 경로 허용 | 상품명/판매가/상세 링크 확인 | 가능 | 홈 HTML에 상품 목록 다수 포함 |
| 파브리크 | 200 | `Allow: /`, 로그인/장바구니/admin 등만 차단 | Imweb `data-product-properties`, 상품명/가격/이미지 확인 | 가능 | `sitemap.xml` 제공. 상품 쇼케이스 구조라 HTML 파싱 가능 |
| 테라스웨어(번개장터) | 200 | `Allow: /`, sitemap 제공 | 정적 HTML에는 없음. 웹 API에서 상품 JSON 확인 | 조건부 가능 | SPA/API 기반. 공식 Open API는 인증 필요 |
| 테라스웨어 | 429 또는 로그인 리다이렉트 | `User-agent: *` 전체 `Disallow: /` | 요청 결과가 네이버 로그인/제한 페이지 | 비권장/불가 | 스마트스토어 공개 페이지 스크래핑 대신 네이버 공식 API/권한 기반 접근 필요 |

## 사이트별 상세

### 로켓샐러드

- URL: https://www.rocketsalad.co.kr/
- `robots.txt`: https://www.rocketsalad.co.kr/robots.txt
- 확인 결과: `User-agent: *`, `Allow: /`, `Disallow: /makeshop/`
- 대상 URL 응답: `200`, `text/html`, 약 220 KB
- HTML 단서: `/shop/shopdetail.html?branduid=...` 상품 상세 링크 다수 확인
- 판정: 가능
- 주의사항: `/makeshop/` 경로는 제외. 상세 페이지는 `/shop/shopdetail.html` 계열이라 현재 robots 기준으로는 막혀 있지 않음.

### 도쿄유즈드

- URL: https://tokyoused.co.kr/
- `robots.txt`: https://tokyoused.co.kr/robots.txt
- 확인 결과: `/admin`, `/api`, `/order`, `/basket`, `/checkout`, `/login`, `/member`, `search.html`, 정렬/필터 URL 차단
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 607 KB
- HTML 단서: Cafe24 `xans-product`, `prdList`, 상품명, 판매가, `/product/...` 상세 링크 확인
- 판정: 가능
- 주의사항: 검색/정렬/필터 URL은 robots 차단 대상. 기본 상품 목록과 상품 상세 중심으로 수집해야 함.

### 헬로스트레인저

- URL: https://hellostranger.co.kr/
- `robots.txt`: https://hellostranger.co.kr/robots.txt
- 확인 결과: Cafe24 공통 차단 경로(`/admin`, `/api`, `/exec/front`, `/member`, `/myshop`, `/protected`, `/skin-*`, 일부 게시판/페이지 파라미터)
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 197 KB
- HTML 단서: Cafe24 상품 목록, 판매가, 상세 링크 확인
- 판정: 가능
- 주의사항: 일부 봇에 `Crawl-delay: 10`이 명시되어 있어 10초 수준의 보수적 딜레이 권장.

### 보노비스타

- URL: https://bonovista.com/category/new/24/
- `robots.txt`: https://bonovista.com/robots.txt
- 확인 결과: 관리자/주문/장바구니/로그인/검색/정렬/필터 차단, `Sitemap: https://bonovista.com/sitemap.xml`
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 543 KB
- HTML 단서: `prdList`, 상품명, 판매가, `/product/...` 상세 링크 확인
- 판정: 가능
- 주의사항: 수집 시작점으로 sitemap과 카테고리 URL을 병행하는 방식이 적합. 정렬/필터 파라미터는 제외.

### 컨트리보이즈

- URL: https://countryboysvtg.co.kr/product/list.html?cate_no=29
- `robots.txt`: https://countryboysvtg.co.kr/robots.txt
- 확인 결과: Cafe24 공통 차단 경로 외 상품 목록/상세 경로 허용
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 2.25 MB
- HTML 단서: `/product/.../category/29/display/1/` 상세 링크 다수 확인
- 판정: 가능
- 주의사항: HTML이 크고 상품 링크가 많아 중복 URL 제거, 페이지 캐싱, 느린 요청 간격이 필요.

### 페스티나렌테

- URL: https://festinalente.kr/product/list2.html?cate_no=60
- `robots.txt`: https://festinalente.kr/robots.txt
- 확인 결과: Cafe24 공통 차단 경로 외 상품 목록/상세 경로 허용
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 103 KB
- HTML 단서: `prdList`, 상품 상세 링크 확인
- 판정: 가능
- 주의사항: 일부 봇에 `Crawl-delay: 10`이 있으므로 과도한 병렬 요청은 피해야 함.

### 마일드컨트리

- URL: https://mildcountry.kr/
- `robots.txt`: https://mildcountry.kr/robots.txt
- 확인 결과: Cafe24 공통 차단 경로 외 상품 목록/상세 경로 허용
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 97 KB
- HTML 단서: 상품명, 판매가, `/product/detail.html?product_no=...` 링크 확인
- 판정: 가능
- 주의사항: 홈에 상품 목록이 포함되어 있어 HTML 파싱만으로 기본 목록 수집 가능.

### 1983빈티지

- URL: https://www.1983vintage.co.kr/product/list.html?cate_no=24
- `robots.txt`: https://www.1983vintage.co.kr/robots.txt
- 확인 결과: Cafe24 공통 차단 경로 외 상품 목록/상세 경로 허용
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 123 KB
- HTML 단서: `xans-product-listnormal`, `/product/.../category/24/display/1/` 링크 확인
- 판정: 가능
- 주의사항: 대상 카테고리 기준 정적 HTML 파싱 가능.

### PAFJ

- URL: https://www.pafj.shop/product/list.html?cate_no=298
- `robots.txt`: https://www.pafj.shop/robots.txt
- 확인 결과: Cafe24 공통 차단 경로 외 상품 목록/상세 경로 허용
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 474 KB
- HTML 단서: Cafe24 상품 구매 정보와 상세 링크 다수 확인
- 판정: 가능
- 주의사항: 상품 수집 시 `product_no` 또는 상세 URL 기준으로 중복 제거 필요.

### 소버서울

- URL: https://soberseoul.co.kr/ALL
- `robots.txt`: https://soberseoul.co.kr/robots.txt
- 확인 결과: `Allow: /`, `/site_join`, `/login`, `/logout.cm`, `/shop_cart`, `/?mode*`, `/admin` 차단, `Sitemap: https://soberseoul.co.kr/sitemap.xml`
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 493 KB
- HTML 단서: Imweb 쇼핑 위젯, `/ALL/?idx=...` 상품 링크, 상품 수 `2225`, 더보기 페이지 정보 확인
- 판정: 가능
- 주의사항: 더보기 방식의 페이징이 있으므로 Ajax/페이지 파라미터 요청은 브라우저 동작을 관찰한 뒤 낮은 빈도로 사용해야 함.

### 매그놀리아미스

- URL: https://magnoliamiss.com/
- `robots.txt`: https://magnoliamiss.com/robots.txt
- 확인 결과: Cafe24 공통 차단 경로 외 상품 목록/상세 경로 허용
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 261 KB
- HTML 단서: 상품명, 판매가, `/product/detail.html?product_no=...` 링크 다수 확인
- 판정: 가능
- 주의사항: 홈 HTML에 상품 목록이 포함되어 있어 기본 목록 수집 난이도 낮음.

### 파브리크

- URL: https://fabrique.co.kr/
- `robots.txt`: https://fabrique.co.kr/robots.txt
- 확인 결과: `Allow: /`, `/site_join`, `/login`, `/logout.cm`, `/shop_cart`, `/?mode*`, `/admin` 차단, `Sitemap: https://fabrique.co.kr/sitemap.xml`
- 대상 URL 응답: `200`, `text/html; charset=utf-8`, 약 1.10 MB
- HTML 단서: Imweb `data-product-properties`에 상품 `idx`, 상품명, 가격, 이미지 URL 포함
- 판정: 가능
- 주의사항: 페이지가 크므로 HTML 다운로드 캐싱 권장. 상품 상세 URL은 `/kids/?idx=...` 등 메뉴별 `idx` 구조로 보임.

### 테라스웨어(네이버 스마트 스토어)

- URL: https://smartstore.naver.com/terracewear?
- `robots.txt`: https://smartstore.naver.com/robots.txt
- 확인 결과: `User-agent: *`에 대해 `Disallow: /`
- 대상 URL 응답: 반복 확인 중 `429`, 또는 `https://nid.naver.com/nidlogin.login?...` 로그인 페이지로 리다이렉트
- HTML 단서: 상품 페이지가 아니라 네이버 로그인/제한 페이지가 내려옴
- 판정: 비권장/불가
- 주의사항: robots 기준으로 전체 차단이며 플랫폼 차원의 자동화 제한이 강함. 수집이 필요하면 네이버 커머스/스마트스토어 공식 API, 판매자 권한, 또는 별도 계약 경로를 사용해야 함.

### 테라스웨어(번개장터)

- URL: https://m.bunjang.co.kr/shops/1545488/products
- `robots.txt`: https://m.bunjang.co.kr/robots.txt
- 확인 결과: `User-agent: *`, `Allow: /`, `Sitemap: https://m.bunjang.co.kr/sitemap.xml`
- 대상 URL 응답: `200`, `text/html`, 약 3.3 KB
- HTML 단서: 원본 HTML은 SPA 셸 구조이며 `<div id="root"></div>`만 있고 상품 목록은 직접 포함되지 않음. `meta robots`는 `noimageindex, noarchive`이며 `noindex`/`nofollow`는 아님.
- API 확인: 프론트엔드가 사용하는 `https://api.bunjang.co.kr/api/search/v8/mw/product/specs/shop?uid=1545488` 요청에서 상품 JSON 응답 확인. 응답에는 상품명, 가격, 상태, 이미지 템플릿, 상품 ID, 판매자 UID가 포함되며 `totalCount: 380`으로 확인됨.
- 프로필 확인: `https://api.bunjang.co.kr/api/1/shop/1545488/cached_profile.json`은 `200 application/json`으로 응답하며 판매자명 `테라스웨어`, 프로샵 여부, 팔로워 수, 상품 통계 등 공개 프로필 데이터를 반환함.
- 공식 API: 번개장터 Open API의 상품 검색 API는 `https://openapi.bunjang.co.kr/api/v1/products` 형태이며 인증 토큰이 필요함. 인증 없이 호출 시 `401 MISSING_AUTH_TOKEN` 확인. 참고 문서: https://api.bgzt.guide/api-10622550
- 판정: 조건부 가능
- 주의사항: 정적 HTML 파싱 방식으로는 수집할 수 없고, 웹 API 또는 브라우저 렌더링 기반 수집이 필요함. 운영 수집은 비공식 웹 API 의존보다 번개장터 공식 Open API/권한 기반 접근을 우선 검토해야 함.

## 구현 권장사항

- `robots.txt`를 매 실행 전 캐시 갱신하고, 차단 경로는 수집 대상에서 제외한다.
- User-Agent는 식별 가능하게 둔다. 예: `CompanyCrawler/1.0 (+contact@example.com)`
- 병렬 요청은 피하고, 최소 2-5초 딜레이를 둔다. `Crawl-delay: 10`이 보이는 사이트는 10초 기준으로 보수적으로 맞춘다.
- 카테고리/상품 상세 URL은 정규화한다. `sort`, `filter`, 검색 URL, 장바구니/로그인/회원/관리자 경로는 제외한다.
- 상품 중복 제거 기준은 Cafe24는 `product_no` 또는 `/product/.../{id}/`, MakeShop은 `branduid`, Imweb은 `idx`가 적합하다.
- 가격/재고/품절 상태는 변동성이 크므로 짧은 TTL 캐시를 사용하고, 이미지 파일은 과도하게 재다운로드하지 않는다.
- CAPTCHA, 로그인, `429`, `403`이 나오면 우회하지 말고 수집을 중단하거나 공식 API/허가 절차로 전환한다.

## 최종 결론

총 14개 대상 중 12개 사이트는 공개 상품 페이지의 정적 HTML 기준으로 크롤링 구현이 가능하고, 번개장터 테라스웨어는 SPA/API 기반이라 조건부 가능하다. 네이버 스마트스토어 테라스웨어는 `robots.txt` 전체 차단과 네이버의 접근 제한 응답이 확인되어 일반 웹 크롤러 대상에서 제외하는 것이 맞다. 실제 운영 전에는 각 사이트의 이용약관, 수집 목적, 요청량, 개인정보/저작권 이슈를 별도로 확인해야 한다.
