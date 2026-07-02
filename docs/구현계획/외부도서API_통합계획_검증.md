# 외부 도서 API 통합 계획 및 검증

작성일: 2026-07-02  
대상 프로젝트: `C:\Users\kjm90\Desktop\ABC`  
참고 프로젝트: `C:\Users\kjm90\Desktop\bookapitest`

## 1. 목표

`bookapitest`에서 검증한 외부 도서 API 기능을 `ABC` 백엔드에 붙이기 위한 구현 직전 계획이다. 이 문서는 계획과 검증만 다루며, 실제 구현 코드는 아직 작성하지 않는다.

이번 구현 회의의 1차 범위는 ISBN13 단건 조회, `book_isbn_temp` 저장, 관리자 승인 후 실제 `book` 이관까지로 잡는다.

```text
관리자가 ISBN13 입력
-> 정보나루/네이버/알라딘 단건 조회
-> 결과 병합
-> 기존 DB 중복 여부 확인
-> book_isbn_temp에 저장/upsert
-> 관리자가 필수값 보완 후 승인
-> 실제 book 관련 테이블 생성
-> book_isbn_temp.status_cd=MERGED
```

1차 범위:

| 포함 | 제외 |
| --- | --- |
| ISBN13 단건 정규화/검증 | 키워드 검색 수집 |
| 외부 API 단건 조회 | 인기/급상승 도서 수집 |
| 병합 preview 응답 | ISBN 목록 bulk fetch |
| `book_isbn_temp` 저장/upsert | 도서관 API 확장 |
| temp 후보 목록 조회 | 도서관 결과 DB 저장 |
| temp 승인 후 실제 `book` 생성 | 파일 업로드 기반 뷰어 |
| 기존 `book.isbn` 중복 확인 | 키워드/인기/급상승 자동 수집 |
| 같은 내용으로 보이는 도서 warning | 대량 배치 수집 |

사용자 도서 검색 API(`/api/v1/books/search`)는 기존 DB 검색을 유지한다. 외부 API 실시간 검색은 속도, 장애, 호출 한도, 키 노출 리스크가 있으므로 관리자 단건 수집/승인 흐름으로 먼저 검증한다.

## 2. 현재 확인한 근거

### 2.1 `bookapitest`

핵심 파일:

| 파일 | 확인 내용 |
| --- | --- |
| `src/main/java/com/test/bookapitest/test/InfoNaruApiTest.java` | `http://data4library.kr/api/srchBooks` 호출. 현재 테스트 실행 결과 연결은 성공하지만 결과는 0건. |
| `src/main/java/com/test/bookapitest/test/NaverApiTest.java` | `https://openapi.naver.com/v1/search/book.json`을 ISBN으로 조회. 저자, 이미지, 설명 수신 성공. |
| `src/main/java/com/test/bookapitest/test/AladinApiTest.java` | `https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx`를 ISBN13으로 조회. 출판일, 카테고리 ID, 표지, 설명 수신 성공. |
| `src/main/resources/application.yml` | 외부 API 키가 평문으로 존재. 통합 전에 환경변수 이동 및 키 로테이션 필요. |

실행 검증:

```text
C:\Users\kjm90\Desktop\ABC\gradlew.bat -p C:\Users\kjm90\Desktop\bookapitest compileJava --console=plain
```

결과: 성공.

```text
C:\Users\kjm90\Desktop\ABC\gradlew.bat -p C:\Users\kjm90\Desktop\bookapitest bootRun --console=plain --args="--spring.main.web-application-type=none"
```

결과:

| API | 결과 |
| --- | --- |
| 정보나루 `srchBooks` | 연결 성공, 전체 건수 0 |
| 네이버 도서 검색 | 연결 성공, ISBN `9788966260959` 데이터 수신 |
| 알라딘 ItemLookUp | 연결 성공, ISBN `9788966260959` 데이터 수신 |

주의: `bookapitest build`는 `bootJar` 단계에서 실패했다. 원인은 `bookapitest`의 Spring Boot 3.3.0 플러그인과 현재 Gradle 9.5.1 조합의 호환성 문제다. 컴파일과 실제 API 호출은 검증되었으므로, `ABC`에는 `bookapitest`의 `build.gradle`을 옮기지 말고 로직만 `ABC`의 Spring Boot 4.0.7 기준으로 재작성한다.

### 2.2 `ABC`

핵심 파일:

| 파일 | 확인 내용 |
| --- | --- |
| `src/main/resources/schema.sql` | `book_isbn_temp`가 이미 존재한다. 네이버/알라딘 응답 상태와 수집 상태 코드도 설계되어 있다. |
| `src/main/java/com/acorn/abc/batch/domain/BookIsbnTemp.java` | `book_isbn_temp` 엔티티가 존재한다. 현재 mutator/repository/service가 없고, setter/builder/all-args 생성자도 없어 수집 결과를 채울 수 있는 도메인 메서드 추가가 필요하다. |
| `src/main/java/com/acorn/abc/book/domain/Book.java` | `isbn`, `cover_image_url`, `pub_date`가 있어 외부 API 결과 이관 가능. |
| `src/main/java/com/acorn/abc/library` | `GET /api/v1/books/{bookId}/libraries` DB 기반 구현이 이미 있다. |
| `docs/API 명세/api-spec(final).md` | 관리자 도서 등록, `API-LIBRARY-001` 명세가 존재한다. |
| `docs/데이터베이스/erd/ebook-rental-erd.md` | 도서관 API 갱신 방식은 개발 단계 결정 사항으로 남아 있다. |

실행 검증:

```text
.\gradlew.bat test --console=plain
```

결과: `BUILD SUCCESSFUL`.

## 3. 외부 API별 역할 정리

| Provider | 사용할 API | 역할/단계 | 저장 후보 |
| --- | --- | --- | --- |
| 정보나루 | `srchDtlList?isbn13=` | ISBN 기반 도서 상세의 1순위 공공 데이터 | 제목, 저자, 출판사, ISBN13, 표지, 설명 |
| 정보나루 | `libSrchByBook?isbn=&region=` | 2차 이후 ISBN 보유 도서관 목록 조회 | 도서관명, 코드, 주소, 위도, 경도 |
| 정보나루 | `bookExist?libCode=&isbn13=` | 2차 이후 가까운 도서관 일부의 실시간 소장/대출 가능 여부 확인 | `hasBook`, `loanAvailable` |
| 네이버 | `/v1/search/book.json?query={isbn13}` | 표지/소개/저자 보강, fallback | author, image, description, publisher, pubdate |
| 알라딘 | `ItemLookUp.aspx?itemIdType=ISBN13&ItemId={isbn13}` | 출판일, 표지, 카테고리 ID 보강 | pubDate, cover, categoryId, description |

`bookapitest`의 정보나루 `srchBooks`는 키워드 검색용 성격이 강하고, 현재 무키워드 테스트 결과도 0건이다. 1차 단건 조회에는 사용하지 않는다. ISBN 기반 도서 등록 보강에는 `srchDtlList`를 추천한다. 실제 호출 확인 결과 `srchDtlList`는 ISBN `9788966260959`로 제목/저자/출판사/표지/설명을 정상 반환했다.

키워드 검색, 인기 도서, 급상승 도서 수집은 단건 조회 구현과 운영 가능성이 확인된 뒤 2차 이후에 다시 설계한다.

공식 문서 확인:

- 정보나루 Open API 활용방법: `libSrch`, `srchDtlList`, `bookExist`, `libSrchByBook`, `srchBooks` 예시 제공.
- 네이버 책 검색 API: `/v1/search/book.json`, `query`, `display`, 인증 헤더 요구.
- 알라딘 상품 API: `ItemLookUp.aspx`, `itemIdType=ISBN13`, `ItemId`, `Version=20131101` 예시 제공.

## 4. 추천 아키텍처

### 4.1 패키지

```text
com.acorn.abc.external.book
  config
    BookApiProperties
    BookApiClientConfig
  client
    InfoNaruBookClient
    NaverBookClient
    AladinBookClient
  dto
    provider별 raw response dto
    공통 BookMetadataResult
  service
    BookMetadataAggregateService

com.acorn.abc.admin.book
  controller
    AdminBookMetadataController
    AdminBookIsbnTempController
  dto
    ExternalBookLookupResponse
    ExternalBookProviderStatus
    ExternalBookDuplicateInfo
    BookIsbnTempFetchRequest
    BookIsbnTempApproveRequest

com.acorn.abc.batch
  repository
    BookIsbnTempRepository
  service
    BookIsbnTempService

2차 이후:
com.acorn.abc.admin.book
  controller
    AdminBookBulkImportController
com.acorn.abc.external.book.service
  LibraryExternalLookupService
com.acorn.abc.external.book.dto
  LibraryHoldingResult
```

`bookapitest`처럼 `CommandLineRunner`에서 콘솔 출력하는 구조는 옮기지 않는다. `ABC`에서는 재사용 가능한 `RestClient` 기반 client/service로 분리하고, 테스트는 mock 서버 또는 fake client로 검증한다.

### 4.2 설정

`application.properties`에는 실제 키를 쓰지 않는다.

```properties
external.book.infonaru.key=${INFONARU_KEY:}
external.book.naver.clients[0].id=${NAVER_CLIENT_ID_1:}
external.book.naver.clients[0].secret=${NAVER_CLIENT_SECRET_1:}
external.book.aladin.keys[0]=${ALADIN_TTB_KEY_1:}
external.book.timeout-ms=${BOOK_API_TIMEOUT_MS:5000}
external.book.enabled=${BOOK_API_ENABLED:false}
```

`.env.example`에는 키 이름만 추가한다. `bookapitest`에 평문 키가 남아 있으므로 실제 통합 전 키 로테이션을 권장한다.

### 4.3 메타데이터 병합 우선순위

ISBN13 정규화:

```text
입력값에서 하이픈/공백 제거
13자리 숫자만 허용
book.isbn은 CHAR(13)이므로 ISBN10은 1차 구현에서 거부 또는 별도 변환 유틸 검증 후 지원
```

필드 우선순위:

| 최종 필드 | 우선순위 |
| --- | --- |
| title | 정보나루 `bookname` -> 알라딘 `title` -> 네이버 `title` |
| author | 정보나루 `authors` -> 네이버 `author` -> 알라딘 `author` |
| publisher | 정보나루 `publisher` -> 알라딘 `publisher` -> 네이버 `publisher` |
| coverImageUrl | 알라딘 Big cover -> 정보나루 `bookImageURL` -> 네이버 `image` |
| description | 가장 긴 정상 설명 또는 정보나루 -> 알라딘 -> 네이버 순 |
| pubDate | 알라딘 `yyyy-MM-dd` -> 네이버 `yyyyMMdd` -> 정보나루 출판연도 보정 |
| aladinCategoryId | 알라딘 `categoryId`를 temp 참고값으로 저장/응답 |

알라딘 `categoryId`는 바로 `category.category_id`로 연결하지 않는다. 기존 DDL 주석에도 별도 매핑 로직 필요가 명시되어 있다. 1차 구현에서는 `book_isbn_temp.aladin_category_id`에 참고값으로 저장하고, 승인 화면에서는 관리자가 내부 카테고리를 직접 선택하게 한다.

## 5. DB 계획 및 검증

### 5.1 1차 DB 전략: temp 저장 후 승인 이관

1차는 신규 DDL 없이 기존 `book_isbn_temp`를 수집 후보 테이블로 사용한다.

| DB 사용 | 방식 |
| --- | --- |
| `book_isbn_temp` | ISBN13 단건 조회 결과를 생성/갱신한다. 같은 ISBN이면 insert가 아니라 update/upsert |
| `book` | 승인 전 같은 ISBN이 이미 등록된 도서인지 재확인하고, 승인 성공 시 생성 |
| `publisher` | `publisher_name` 기준 기존 row 재사용, 없으면 생성 |
| `author`/`book_author` | 저자명 기준 기존 row 조회 후 재사용 또는 생성, `book_author`로 연결 |
| `book_detail` | 외부 API description을 기본 설명으로 넣고, 관리자가 수정 가능 |
| `book_page` | 승인 요청의 `pages[]`로 생성. `total_pages` 계산 기준 |
| `book_category` | 관리자가 선택한 내부 `categoryIds[]`로 연결 |
| `category` | 알라딘 `categoryId`를 자동 FK로 쓰지 않음. 내부 카테고리 선택 필요 |

검증된 제약:

| 항목 | 실제 상태 | 1차 처리 |
| --- | --- | --- |
| `book.isbn` | `CHAR(13) NULL`, `UNIQUE` | 같은 ISBN이면 신규 등록 차단 |
| `book_isbn_temp.isbn13` | `CHAR(13) NOT NULL`, `UNIQUE` | 1차 저장 단계에서 upsert 필요 |
| `publisher.publisher_name` | `UNIQUE` | 최종 등록 시 기존 출판사 재사용 |
| `author.author_name` | UNIQUE 없음, index만 존재 | 최종 등록 시 코드 레벨 중복 조회 필요 |
| `book.title + author + publisher` | UNIQUE 없음 | 1차 승인 화면에서 warning만 제공 |

`book.isbn`은 NULL 허용이지만, 이번 기능은 ISBN13 입력이 필수이므로 NULL 중복 정책은 범위 밖이다.

승인 이관은 한 트랜잭션으로 처리한다. 중간에 실패하면 `book` 관련 row와 `book_isbn_temp.status_cd` 변경이 같이 rollback되어야 한다.

### 5.2 같은 내용 도서 중복 처리

중복은 두 단계로 나눈다.

| 케이스 | DB 근거 | 추천 처리 |
| --- | --- | --- |
| 같은 ISBN13이 이미 `book`에 있음 | `uk_book_isbn UNIQUE (isbn)` | `duplicateStatus=BOOK_EXISTS` 반환. 신규 등록 버튼 비활성화 또는 기존 도서 수정 화면으로 이동 |
| 같은 ISBN13이 `book_isbn_temp`에 있음 | `uk_book_isbn_temp_isbn13 UNIQUE (isbn13)` | 1차 저장 단계에서 insert 금지, 기존 row update/upsert |
| 제목+저자+출판사가 거의 같지만 ISBN이 다름 | 현재 강제 제약 없음 | `duplicateStatus=POSSIBLE_DUPLICATE` warning. 개정판/번역판/전자책 ISBN 차이가 있을 수 있어 자동 차단하지 않음 |
| 제목만 같음 | 현재 강제 제약 없음 | warning도 약하게 표시. 같은 제목의 다른 책 가능성 큼 |
| ISBN 형식 오류 | `book.isbn`은 CHAR(13), 기능 요구는 ISBN13 | 외부 API 호출 전 400 처리 |

1차 preview 응답에는 중복 정보를 포함한다.

```json
{
  "duplicate": {
    "status": "BOOK_EXISTS",
    "existingBookId": 12,
    "message": "이미 같은 ISBN으로 등록된 도서입니다."
  }
}
```

추천 enum:

| 값 | 의미 |
| --- | --- |
| `NONE` | 중복 후보 없음 |
| `BOOK_EXISTS` | 같은 ISBN의 내부 도서가 이미 있음 |
| `POSSIBLE_DUPLICATE` | 같은 내용으로 보이는 내부 도서가 있음 |

최종 도서 등록 API를 만들 때도 같은 검증을 반복해야 한다. preview에서 막았더라도 동시 요청이나 수동 호출이 가능하므로, 등록 직전 `book.isbn` 재조회 후 중복이면 409 Conflict를 반환해야 한다.

### 5.3 1차 temp 저장 단계: `book_isbn_temp`

1차에서 후보 저장은 신규 테이블 없이 `book_isbn_temp`를 사용한다.

현재 컬럼:

```text
isbn13
book_title
publisher
author
cover_image_url
description
pub_date
aladin_category_id
naver_res_status
aladin_res_status
status_cd
created_at
updated_at
```

상태 코드 사용:

| 컬럼 | 값 | 의미 |
| --- | --- | --- |
| `naver_res_status`, `aladin_res_status` | 0 | 미시도 |
|  | 1 | 성공, 데이터 있음 |
|  | 2 | 성공, 데이터 없음 |
|  | 3 | 실패, 재시도 가능 |
|  | 4 | 실패, 재시도 불가 |
| `status_cd` | 0 | 수집 중/PENDING |
|  | 1 | 관리자 등록 후보 READY |
|  | 2 | `book` 이관 완료 MERGED |

주의: 정보나루 상태 컬럼은 현재 없다. 1차 저장 단계에서는 정보나루 실패를 전체 `status_cd`와 서비스 로그로 관리하고, 네이버/알라딘 상태는 기존 컬럼에 기록한다. 정보나루 상태까지 DB에서 보고 싶다면 2차 DDL 변경으로 `infonaru_res_status`를 추가한다.

1차 temp DB 검증 기준:

| 항목 | 통과 기준 |
| --- | --- |
| 스키마 정합성 | `book_isbn_temp` 컬럼과 `BookIsbnTemp` 엔티티가 일치해야 한다. |
| 엔티티 생성/갱신 | `BookIsbnTemp`에 신규 row 생성용 정적 팩토리/생성자와 기존 row 갱신용 메서드가 있어야 한다. |
| 중복 방지 | `isbn13` UNIQUE로 같은 ISBN 재수집 시 insert가 아니라 update/upsert가 되어야 한다. |
| 이관 전 검증 | `book.isbn` UNIQUE와 충돌하면 새 `book` 생성이 아니라 기존 도서 연결/중단으로 처리해야 한다. |
| 날짜 변환 | `book_isbn_temp.pub_date`는 문자열이지만 `book.pub_date`는 DATE이므로 이관 시 파싱 실패 처리가 있어야 한다. |
| 카테고리 | 알라딘 categoryId를 내부 categoryId로 오인해 자동 FK 연결하지 않는다. |

### 5.4 승인 시 확정/보완해야 하는 값

정책 문서(`docs/기획문서들/멘토링전기획문서/ERD-정책-확정-필요사항 수정후.md`) 기준으로 이미 정해진 값과, 승인 화면에서 아직 입력해야 하는 값을 분리한다.

이미 정책 문서에 정해진 값:

| 항목 | 확정값 | 승인 처리 |
| --- | --- | --- |
| 도서 대여 타입 운영 방향 | 유료 책이 대부분, 무료 책은 이벤트/소수 | 승인 화면 기본 선택값은 `PAID`. 단, 책마다 관리자가 `FREE`/`PAID` 최종 선택 |
| 기본 대여 기간 | 유료 14일 고정, 무료 7일 | `defaultRentalDays`는 `rentalType` 기준으로 서버가 자동 세팅 또는 검증 |
| 기본 대여 가격 | 유료 기본 5000원 | `PAID` 기본값은 5000원. `FREE` 선택 시 0원으로 검증 |
| 기본 도서 상태 | `AVAILABLE` | 승인 기본 상태는 `AVAILABLE` |
| 무료 책 정책 | 무료 책은 기간 종료 후 유료 책 전환 가능 | 1차 승인에서는 `rentalType`만 저장하고 전환 자동화는 범위 밖 |
| 누적 대여 수 | `BOOK.rental_count`에 저장, 결제 완료 시 증가 | 신규 도서 생성 시 0 |
| 평점 | `BOOK.average_rating`에 저장, 별점 table 기준 갱신 | 신규 도서 생성 시 `0.00` |
| 리뷰 수 | `BOOK.review_count`에 저장, ACTIVE 리뷰 기준 갱신 | 신규 도서 생성 시 0 |

승인 화면에서 관리자가 보완해야 하는 값:

| 대상 | 필수값 | 이유 |
| --- | --- | --- |
| `book` | `rentalType` | 책별로 `FREE`/`PAID` 선택 필요. 기본 선택값은 `PAID` |
| `book` | `rentalPrice` | NOT NULL. 기본 5000원. `FREE`면 0, `PAID`면 0보다 커야 함 |
| `book` | `status` | NOT NULL, 기본값 `AVAILABLE` |
| `book_category` | `categoryIds[]` | 내부 카테고리 FK. 알라딘 categoryId 자동 사용 금지. 관리자가 직접 선택 |
| `book_page` | `pages[]` | 본문 페이지 없는 승인 허용. 단, page row가 있으면 `page_no`, `page_content` NOT NULL이고 페이지 번호 중복 금지 |

시스템이 자동으로 채워야 하는 값:

| 대상 | 값 | 처리 |
| --- | --- | --- |
| `book.default_rental_days` | `PAID=14`, `FREE=7` | 정책 문서 기준 자동 세팅 |
| `book.total_pages` | `pages[]` 기준 | `pages[]` 개수 또는 최대 `pageNo` 기준으로 계산 |
| `book.rental_count` | 0 | 신규 도서 생성 기본값 |
| `book.average_rating` | `0.00` | `BigDecimal.ZERO`로 명시 세팅 |
| `book.review_count` | 0 | 신규 도서 생성 기본값 |
| `publisher.status` | `ACTIVE` | 신규 publisher 생성 기본값 |
| `author.status` | `ACTIVE` | 신규 author 생성 기본값 |

외부 API에서 가져와 승인 화면에 기본값으로 넣을 수 있는 값:

| 대상 | 기본값 출처 |
| --- | --- |
| `book.title` | `book_isbn_temp.book_title` |
| `book.isbn` | `book_isbn_temp.isbn13` |
| `book.cover_image_url` | `book_isbn_temp.cover_image_url` |
| `book.pub_date` | `book_isbn_temp.pub_date` 파싱 |
| `publisher.publisher_name` | `book_isbn_temp.publisher` |
| `author.author_name` | `book_isbn_temp.author` 파싱/분리 |
| `book_detail.description` | `book_isbn_temp.description` |

현재 `Book`, `Publisher`, `Author`, `BookDetail`, `BookPage`, `BookAuthor`, `BookCategory`, `BookIsbnTemp` 엔티티는 protected no-args 생성자와 getter 중심이다. 실제 승인 이관 구현 전에 각 엔티티에 정적 팩토리/생성자/도메인 메서드를 추가해야 한다.

현재 `src/main/resources/schema.sql`에는 `book_file` 테이블이 없다. 따라서 1차 승인 이관은 파일 업로드 없이 `book_page` 텍스트 페이지 기반으로만 처리한다.

### 5.5 도서관 DB 저장 추천안

현재 `library` 테이블은 `libCode` 컬럼이 없다.

```text
library_id
library_name
address
latitude
longitude
status
```

따라서 정보나루 도서관 결과를 영구 저장/갱신하려면 `lib_code` UNIQUE가 필요하다. 하지만 이번 1차 범위는 ISBN 단건 수집, temp 저장, 승인 이관이므로 도서관 live lookup은 제외한다.

단계별 추천:

| 단계 | DB 전략 | 추천 이유 |
| --- | --- | --- |
| 1차 | 범위 밖 | ISBN 단건 수집/승인 이관을 먼저 검증 |
| 2차 | DB 저장 없이 `libSrchByBook` 결과를 응답에 합성 | DDL 변경 없이 `API-LIBRARY-001` 확장 가능 |
| 3차 | `library.lib_code`, `region`, `dtl_region`, `updated_at` 추가 | 도서관 캐시와 거리순 검색 성능 확보 |
| 4차 | `library_book.source`, `source_updated_at` 또는 별도 sync log 추가 | API 동기화 이력과 운영 장애 추적 가능 |

## 6. API 계획 및 검증

### 6.1 관리자 단건 수집/temp/승인 API

1차 권장 신규 API 1: preview 조회

```http
GET /api/v1/admin/books/external/lookup?isbn13=9788966260959
```

역할:

1. ISBN13 형식을 검증한다.
2. 같은 ISBN이 `book`에 이미 있는지 확인한다.
3. 제목/저자/출판사 기준 의심 중복 도서를 조회한다.
4. 외부 API 3곳을 호출해 병합 preview를 반환한다.
5. DB에는 저장하지 않는다.

예상 응답:

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 성공했습니다.",
  "data": {
    "isbn13": "9788966260959",
    "title": "클린코드 :애자일 소프트웨어 장인 정신",
    "author": "로버트 C. 마틴 지음 ;이해영 옮김",
    "publisher": "인사이트",
    "coverImageUrl": "https://...",
    "description": "...",
    "pubDate": "2013-12-24",
    "aladinCategoryId": "2724",
    "providers": {
      "infonaru": "SUCCESS_HAS_DATA",
      "naver": "SUCCESS_HAS_DATA",
      "aladin": "SUCCESS_HAS_DATA"
    },
    "duplicate": {
      "status": "NONE",
      "existingBookId": null,
      "candidates": []
    },
    "warnings": []
  }
}
```

같은 ISBN이 이미 등록된 경우:

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 성공했습니다.",
  "data": {
    "isbn13": "9788966260959",
    "title": "클린코드 :애자일 소프트웨어 장인 정신",
    "duplicate": {
      "status": "BOOK_EXISTS",
      "existingBookId": 12,
      "candidates": [
        {
          "bookId": 12,
          "title": "클린코드",
          "isbn": "9788966260959",
          "status": "AVAILABLE"
        }
      ]
    },
    "warnings": ["이미 같은 ISBN으로 등록된 도서입니다."]
  }
}
```

1차 권장 신규 API 2: temp 저장/upsert

```http
POST /api/v1/admin/book-isbn-temps/fetch
```

요청:

```json
{
  "isbn13": "9788966260959"
}
```

역할:

1. ISBN13 형식을 검증한다.
2. `book.isbn` 중복을 확인한다.
3. 외부 API 3곳을 호출해 결과를 병합한다.
4. `book_isbn_temp.isbn13` 기준으로 insert 또는 update한다.
5. 최소 필드가 확보되면 `status_cd=1(READY)`로 둔다.

응답:

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 성공했습니다.",
  "data": {
    "tempId": 35,
    "isbn13": "9788966260959",
    "statusCd": 1,
    "duplicate": {
      "status": "NONE",
      "existingBookId": null,
      "candidates": []
    }
  }
}
```

같은 ISBN이 이미 temp에 있으면 새 row를 만들지 않고 기존 row를 갱신한다.

1차 권장 신규 API 3: temp 후보 목록

```http
GET /api/v1/admin/book-isbn-temps?status=READY&page=0&size=20
```

역할: 관리자가 승인할 수 있는 후보 목록을 조회한다.

1차 권장 신규 API 4: temp 승인 후 실제 book 생성

```http
POST /api/v1/admin/book-isbn-temps/{tempId}/approve
```

요청:

```json
{
  "title": "클린코드 :애자일 소프트웨어 장인 정신",
  "publisherName": "인사이트",
  "authors": ["로버트 C. 마틴", "이해영"],
  "categoryIds": [1],
  "rentalType": "PAID",
  "rentalPrice": 5000,
  "status": "AVAILABLE",
  "detail": {
    "description": "...",
    "tableOfContents": null,
    "publisherReview": null,
    "detailContent": null
  },
  "pages": [
    {
      "pageNo": 1,
      "pageContent": "..."
    }
  ]
}
```

`pages`는 빈 배열을 허용한다. 이 경우 `book.total_pages=0`으로 생성하고 `book_page` row는 만들지 않는다.

역할:

1. `book_isbn_temp`를 조회한다.
2. temp가 `READY`인지 확인한다.
   - `MERGED` temp 재승인은 `409 Conflict`로 차단한다.
3. 승인 직전 `book.isbn` 중복을 다시 확인한다.
4. `publisher`를 이름 기준 upsert한다.
5. `author`를 이름 기준 조회/생성하고 `book_author`를 만든다.
6. `book`, `book_detail`, `book_category`, `book_page`를 생성한다.
7. `book.default_rental_days`는 `rentalType` 기준으로 `PAID=14`, `FREE=7`을 세팅한다.
8. `book.total_pages`는 `pages[]` 개수 또는 최대 pageNo 기준으로 세팅한다.
9. `book.rental_count=0`, `book.average_rating=0.00`, `book.review_count=0`을 명시 세팅한다.
10. temp를 `status_cd=2(MERGED)`로 변경한다.

응답:

```json
{
  "success": true,
  "code": "CREATED",
  "message": "생성되었습니다.",
  "data": {
    "bookId": 12,
    "tempId": 35,
    "status": "AVAILABLE"
  }
}
```

승인 시 중복 ISBN이 발견되면 `409 Conflict`로 중단하고 temp는 `READY`로 유지한다. 승인 중 `book_page`, `book_category`, `book_detail` 저장에 실패하면 `book` 생성까지 전체 rollback한다.

2차 이후 API:

| API | 역할 |
| --- | --- |
| `POST /api/v1/admin/book-isbn-temps/bulk-fetch` | ISBN 여러 개 수집 |
| `POST /api/v1/admin/books/external/search` | 키워드/인기/급상승 기반 후보 수집 |

### 6.2 도서 보유 도서관 API: 2차 이후

기존 API:

```http
GET /api/v1/books/{bookId}/libraries
```

현재 역할: DB의 `library_book` 기준 보유 도서관 목록 반환.

2차 이후 권장 확장:

```http
GET /api/v1/books/{bookId}/libraries?lat=37.5665&lng=126.9780&region=11&limit=10&checkAvailability=true
```

동작:

1. `bookId`로 내부 `book`을 조회하고 `status=AVAILABLE`, `isbn` 존재를 확인한다.
2. `lat/lng` 또는 `region`이 없으면 기존 DB 기반 응답을 유지한다.
3. `region`이 있으면 정보나루 `libSrchByBook`으로 보유 도서관 목록을 조회한다.
4. 응답의 위도/경도와 사용자 `lat/lng`로 Haversine 거리 계산 후 가까운 순으로 정렬한다.
5. `checkAvailability=true`면 상위 N개에만 `bookExist`를 호출해 `hasBook`, `loanAvailable`을 보강한다.
6. 외부 API 장애 시 기존 DB 기반 결과로 fallback하고 `externalStatus` 또는 `warnings`를 응답에 포함한다.

응답 DTO 추천:

```json
{
  "bookId": 1,
  "title": "클린코드",
  "isbn13": "9788966260959",
  "libraries": [
    {
      "libraryCode": "111108",
      "libraryName": "가온도서관",
      "address": "서울...",
      "latitude": 37.5563885,
      "longitude": 127.010869,
      "distanceKm": 1.23,
      "holdingStatus": "AVAILABLE",
      "loanAvailable": true,
      "source": "INFONARU"
    }
  ],
  "externalStatus": "SUCCESS"
}
```

현재 `LibrarySummaryResponse`에는 `libraryCode`, `distanceKm`, `loanAvailable`, `source`가 없다. 기존 응답을 깨지 않으려면 새 DTO를 쓰거나 nullable 필드를 추가한다. 사용자 공개 API라면 하위 호환을 위해 새 DTO를 추천한다.

### 6.3 Security

현재 `SecurityConfig`는 `GET /api/v1/books/{bookId}/libraries`를 공개 API로 허용한다. 이 API 확장은 공개 가능하지만, 외부 API 호출 한도 보호를 위해 다음 제한이 필요하다.

| API | 권한 |
| --- | --- |
| `GET /api/v1/books/{bookId}/libraries` | 공개 유지 가능. 단 `checkAvailability=true`는 rate limit 권장 |
| `GET /api/v1/admin/books/external/lookup` | 관리자 전용 |
| `POST /api/v1/admin/book-isbn-temps/fetch` | 관리자 전용 |
| `GET /api/v1/admin/book-isbn-temps` | 관리자 전용 |
| `POST /api/v1/admin/book-isbn-temps/{tempId}/approve` | 관리자 전용 |

관리자 권한 처리는 현재 `SecurityConfig`에 TODO로 남아 있으므로, 관리자 API를 만들 때 `/api/v1/admin/**` 권한 규칙도 같이 확정해야 한다.

## 7. 결과 검증 계획

### 7.1 DB 검증

구현 후 반드시 확인할 항목:

| 검증 | 방법 | 통과 기준 |
| --- | --- | --- |
| ISBN 중복 조회 | `book`에 같은 ISBN seed 후 preview 호출 | `duplicate.status=BOOK_EXISTS`, `existingBookId` 반환 |
| 의심 중복 조회 | 제목/저자/출판사가 유사한 seed 후 다른 ISBN preview 호출 | `duplicate.status=POSSIBLE_DUPLICATE`, 후보 목록 반환 |
| 중복 없음 | 내부 DB에 없는 ISBN preview 호출 | `duplicate.status=NONE` |
| `book_isbn_temp` upsert | 같은 ISBN을 2회 fetch | row 1개 유지, `updated_at` 갱신 |
| provider 상태 저장 | 네이버/알라딘 성공/없음/실패 fake 응답 | `naver_res_status`, `aladin_res_status`가 정의대로 저장 |
| READY 판정 | 최소 필드 수집 성공 | `status_cd=1` |
| 승인 이관 | READY temp를 approve | `book`, 연결 테이블 생성, temp `status_cd=2` |
| 승인 중복 방어 | approve 직전 같은 ISBN book seed | 409 Conflict, temp는 READY 유지 |
| MERGED 재승인 방어 | MERGED temp를 approve | 409 Conflict, 추가 book 생성 없음 |
| 시스템 기본값 | approve 성공 | `defaultRentalDays=14/7`, `rental_count=0`, `average_rating=0.00`, `review_count=0`, 신규 publisher/author `ACTIVE` |
| 빈 페이지 승인 | `pages=[]` 승인 요청 | book 생성 성공, `total_pages=0`, `book_page` row 없음 |
| 페이지 중복 방어 | page가 있을 때 같은 `pageNo` 2개 승인 요청 | 400 Validation, book 생성 없음 |
| 카테고리 FK 방어 | 없는 `categoryId` 승인 요청 | 전체 rollback, book 생성 없음 |
| book_detail 실패 방어 | book_detail 저장 실패 유도 | 전체 rollback, book 생성 없음 |
| 날짜 파싱 | `2013-12-24`, `20131224`, 빈 값 | 정상 LocalDate 변환 또는 null 처리 |
| H2 테스트 | `.\gradlew.bat test` | 전체 테스트 성공 |
| MySQL validate | 로컬 MySQL `ABC`로 bootRun | Hibernate validate 오류 없음 |

DB 주의사항:

1. `book.isbn` UNIQUE가 최종 방어선이다. preview에서 중복을 알려도, 최종 등록 API에서 다시 중복 조회 후 409 Conflict를 반환해야 한다.
2. `author.author_name`에는 UNIQUE가 없으므로 같은 저자 중복은 코드 레벨 조회/재사용 정책이 필요하다.
3. `book_isbn_temp`에는 정보나루 상태 컬럼이 없으므로, 1차 저장 단계에서는 정보나루를 병합 서비스 내부 상태로만 다룬다.
4. 승인 이관은 트랜잭션이어야 한다. `book` 생성 후 `book_page`에서 실패하면 전체 rollback되어야 한다.
5. `publisherName` 또는 저자명이 비어 신규 `publisher`/`author`를 만들 수 없으면 승인 자체를 400으로 중단한다.

### 7.2 API 검증

구현 후 테스트 구성:

| 레벨 | 테스트 |
| --- | --- |
| Unit | ISBN 정규화, 날짜 파싱, provider result 병합, 중복 상태 판정 |
| Client | `MockRestServiceServer` 또는 fake HTTP server로 정보나루/네이버/알라딘 응답 매핑 |
| Service | 한 provider 실패 시 partial success 유지, 전체 실패 시 명확한 error/warning |
| Repository | `BookRepository` ISBN 조회, 의심 중복 후보 조회, `BookIsbnTempRepository` upsert/조회 |
| Controller | `MockMvc`로 admin lookup/fetch/list/approve 검증 |
| Transaction | approve 중간 실패 시 `book` 관련 row와 temp 상태 rollback 검증 |
| Smoke | 실제 키가 있는 로컬에서만 외부 API 1회 호출 |

실제 API smoke 기준:

| API | 기준 |
| --- | --- |
| 정보나루 `srchDtlList` | ISBN `9788966260959`로 title/author/publisher/isbn13 수신 |
| 네이버 | `items[0].author/image/description` 수신 |
| 알라딘 | `item[0].pubDate/categoryId/cover/description` 수신 |

기본 검증 명령:

```powershell
.\gradlew.bat test --console=plain
```

외부 API smoke는 기본 테스트에 넣지 않는다. CI/팀원 환경에서 키가 없으면 실패하기 때문이다. 별도 profile을 둔다.

```powershell
.\gradlew.bat test --tests "*ExternalApiSmokeTest" -Dspring.profiles.active=external-api
```

### 7.3 결과 검증

최종 사용 시나리오별 통과 기준:

| 시나리오 | 기대 결과 |
| --- | --- |
| 관리자 ISBN preview | DB 저장 없이 3개 provider 상태, 병합 필드, 중복 상태 반환 |
| 관리자 ISBN fetch | `book_isbn_temp`에 row 생성 또는 갱신, READY 상태 반환 |
| temp 후보 목록 | READY 후보가 페이징 목록으로 반환 |
| temp 승인 | 실제 `book`과 연결 테이블 생성, temp MERGED 처리 |
| temp 승인 중복 | 같은 ISBN book이 있으면 409 Conflict, temp READY 유지 |
| 이미 등록된 ISBN preview | `duplicate.status=BOOK_EXISTS`, 기존 `bookId` 반환 |
| 같은 내용 의심 preview | `duplicate.status=POSSIBLE_DUPLICATE`, 후보 목록과 warning 반환 |
| 기존 도서 검색 | 외부 API 통합 후에도 DB 기반 검색 결과와 응답 구조 유지 |
| provider 장애 | 전체 API 500으로 무너뜨리지 않고 가능한 fallback 또는 partial result 반환 |

## 8. 구현 순서 추천

1. 키 정리
   - `bookapitest` 평문 키는 더 이상 신뢰하지 말고 로테이션한다.
   - `ABC/.env.example`에는 변수명만 추가한다.

2. 외부 API properties/config 추가
   - `BookApiProperties`
   - `RestClient` timeout 설정
   - `external.book.enabled=false` 기본값

3. 단건 metadata provider client 작성
   - `InfoNaruBookClient`: `srchDtlList`
   - `NaverBookClient`: book search
   - `AladinBookClient`: ItemLookUp

4. 병합 서비스 작성
   - ISBN 정규화
   - provider별 timeout/error/status 변환
   - field priority 적용
   - warnings 수집
   - `duplicate.status` 판정

5. DB 중복 조회와 temp 저장 작성
   - `BookRepository.existsByIsbn(String isbn)` 또는 `findByIsbn(String isbn)` 추가
   - 제목+저자+출판사 기준 의심 중복 후보 조회 쿼리 추가
   - 같은 ISBN은 hard block 정보, 유사 도서는 warning 정보로 반환
   - `BookIsbnTempRepository` 작성
   - `BookIsbnTemp` 생성/갱신/READY/MERGED 도메인 메서드 추가
   - ISBN 단건 fetch upsert 작성

6. 관리자 preview/temp API 작성
   - `GET /api/v1/admin/books/external/lookup?isbn13=...`
   - `POST /api/v1/admin/book-isbn-temps/fetch`
   - `GET /api/v1/admin/book-isbn-temps?status=READY&page=0&size=20`
   - 관리자 권한 규칙 동시 정리

7. 승인 이관 구현
   - `Book`, `Publisher`, `Author`, `BookDetail`, `BookPage`, `BookAuthor`, `BookCategory` 생성 메서드 추가
   - 승인 요청 DTO validation 작성
   - publisher/author 조회 또는 생성
   - book/book_detail/book_page/book_category/book_author 생성
   - `defaultRentalDays`는 `rentalType` 기준 `PAID=14`, `FREE=7`로 세팅
   - `rental_count=0`, `average_rating=0.00`, `review_count=0` 명시 세팅
   - 성공 시 temp `MERGED`
   - MERGED temp 재승인은 409로 차단
   - page row가 있을 때 pageNo 중복, category FK 실패, book_detail 저장 실패 시 전체 rollback

8. 테스트 작성
   - fake 응답 기반 단위/컨트롤러 테스트
   - 실제 외부 API smoke는 별도 profile
   - 같은 ISBN/의심 중복/중복 없음 케이스 검증
   - temp upsert, approve, rollback 검증

9. 2차 이후 확장
   - ISBN bulk fetch
   - 키워드/인기/급상승 수집 설계
   - 도서관 API live lookup 확장

10. 문서 갱신
   - API 명세에 관리자 단건 preview API 추가
   - API 명세에 temp fetch/list/approve API 추가
   - 1차는 신규 DDL 없음으로 명시
   - 2차 DB 변경이 있으면 `docs/데이터베이스/ddl/schema.sql`과 `src/main/resources/schema.sql` 동시 정리

## 9. 통과 전 유의사항

| 유의사항 | 추천안 |
| --- | --- |
| 평문 키 노출 | 키 로테이션 후 `.env`로만 주입 |
| `srchBooks` 0건 | ISBN 상세는 `srchDtlList`로 대체 |
| `bookapitest build` 실패 | 빌드 파일 이식 금지, 로직만 Spring Boot 4 기준 재작성 |
| 외부 API 장애 | provider별 partial success 구조 사용 |
| 호출 한도 | 사용자 공개 검색에 실시간 외부 호출 금지, admin/batch 위주 |
| 같은 ISBN 중복 | `book.isbn` 조회 후 신규 등록 차단, 최종 등록 시 409 Conflict |
| 같은 내용 의심 중복 | 제목+저자+출판사 후보를 warning으로 제공, 자동 차단 금지 |
| 승인 필수값 | temp metadata만으로는 부족하므로 `rentalType`, `rentalPrice`, `status`, `categoryIds` 등 관리자 입력 필수. `pages[]`는 빈 배열 허용 |
| 기본 대여 기간 | 정책 문서 기준 `PAID=14`, `FREE=7`; 관리자가 임의 결정하지 않고 서버가 세팅/검증 |
| 기본 승인값 | `rentalType=PAID`, `rentalPrice=5000`, `status=AVAILABLE` |
| 초기 집계값 | `rental_count=0`, `average_rating=0.00`, `review_count=0`을 코드에서 명시 세팅 |
| 엔티티 생성 메서드 부재 | `Book` 계열 엔티티에 정적 팩토리/도메인 메서드 추가 필요 |
| 알라딘 categoryId | 내부 category 자동 매핑 금지, 관리자 선택 또는 별도 mapping table |
| 도서관 `libCode` 부재 | 1차 범위 밖, 2차 이후 live lookup 또는 `lib_code` DDL 검토 |
| 관리자 API 권한 | `/api/v1/admin/**` 권한 정책 먼저 확정 |
| 기존 API 호환성 | `/api/v1/books`, `/api/v1/books/search`, 기존 `/libraries` 기본 응답 유지 |
| 테스트 안정성 | 실제 외부 API 테스트는 기본 test에서 제외 |

## 10. 현재 검증 결론

### DB

통과 가능. 1차는 신규 DDL 없이 `book_isbn_temp` 저장/upsert와 실제 `book` 이관까지 가능하다. 다만 승인 이관을 위해 `BookIsbnTemp`뿐 아니라 `Book`, `Publisher`, `Author`, `BookDetail`, `BookPage`, `BookAuthor`, `BookCategory`에 생성/연결 메서드가 필요하다. 정책 문서 기준 기본 대여 기간은 `PAID=14`, `FREE=7`로 확정되어 있으므로 서버 기본값으로 세팅한다. 같은 ISBN 중복은 `book.isbn` UNIQUE와 조회 로직으로 막고, 같은 내용으로 보이는 도서는 warning으로만 제공한다.

### API

통과 가능. `bookapitest` 로직은 컴파일되고 실제 호출도 성공했다. 단 `srchBooks`는 ISBN 상세 수집에 맞지 않으므로 1차 단건 조회에서는 `srchDtlList`를 사용한다. `ABC`에는 콘솔 테스트 클래스가 아니라 client/service/controller 구조로 붙인다.

### 결과

통과 가능. ISBN `9788966260959` 기준으로 네이버/알라딘/정보나루 상세 API 유효 응답을 확인했다. 구현 후에는 관리자 단건 preview, temp 저장/upsert, temp 목록, 승인 이관, provider partial success, ISBN 중복, 의심 중복 케이스를 먼저 검증한다.

최종 추천은 다음이다.

```text
1차: 신규 DDL 없이 admin ISBN13 단건 preview + book_isbn_temp 저장/upsert + 승인 이관
2차: ISBN bulk fetch, 키워드/인기/급상승 수집
3차: 도서관 live lookup, 도서관 캐시 DDL 검토
```
