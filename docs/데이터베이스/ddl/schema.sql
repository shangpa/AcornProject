-- 기준 문서: ebook-rental-erd.md
-- 대상 DBMS: MySQL 8.x
-- 목적: 전자책 대여 서비스 DDL
-- Spring Boot 자동 실행용: CREATE TABLE IF NOT EXISTS 사용 (재실행 시 에러 무시)

-- =====================================================================
-- 책 데이터 수집 파이프라인 임시 테이블
-- 정보나루 → 네이버 → 알라딘 순으로 데이터 보강 후 book 테이블로 이관
-- status_cd: 0=PENDING(수집중), 1=READY(이관가능), 2=MERGED(이관완료)
-- naver_res_status / aladin_res_status: 0=미시도, 1=성공(데이터있음), 2=성공(데이터없음), 3=실패(재시도가능), 4=실패(재시도불가)
-- =====================================================================
CREATE TABLE IF NOT EXISTS book_isbn_temp (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  isbn13              CHAR(13) NOT NULL,
  book_title          VARCHAR(255) NULL,
  publisher           VARCHAR(100) NULL,
  author              VARCHAR(255) NULL,
  cover_image_url     VARCHAR(500) NULL,
  description         TEXT NULL,
  pub_date            VARCHAR(20) NULL,
  aladin_category_id  VARCHAR(20) NULL,
  aladin_category_name VARCHAR(255) NULL,
  naver_res_status    TINYINT NOT NULL DEFAULT 0,
  aladin_res_status   TINYINT NOT NULL DEFAULT 0,
  status_cd           TINYINT NOT NULL DEFAULT 0,
  created_at          DATETIME NOT NULL,
  updated_at          DATETIME NULL,
  CONSTRAINT pk_book_isbn_temp PRIMARY KEY (id),
  CONSTRAINT uk_book_isbn_temp_isbn13 UNIQUE (isbn13)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS member_grade (
  grade_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  grade_name VARCHAR(50) NOT NULL,
  grade_level INT NOT NULL,
  min_payment_amount INT NOT NULL DEFAULT 0,
  grade_period_months INT UNSIGNED NOT NULL,
  maintain_payment_rate DECIMAL(5,2) NOT NULL DEFAULT 10.00,
  grade_description TEXT NULL,
  CONSTRAINT pk_member_grade PRIMARY KEY (grade_id),
  CONSTRAINT uk_member_grade_grade_name UNIQUE (grade_name),
  CONSTRAINT uk_member_grade_grade_level UNIQUE (grade_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS publisher (
  publisher_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  publisher_name VARCHAR(100) NOT NULL,
  publisher_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_publisher PRIMARY KEY (publisher_id),
  CONSTRAINT uk_publisher_publisher_name UNIQUE (publisher_name),
  CONSTRAINT chk_publisher_status CHECK (publisher_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [주의] 외부 API(네이버)에서 저자명 수집 시 author_name 기준으로 중복 체크 후 upsert 처리 필요 (코드 레벨 처리)
CREATE TABLE IF NOT EXISTS author (
  author_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  author_name VARCHAR(100) NOT NULL,
  author_intro TEXT NULL,
  author_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_author PRIMARY KEY (author_id),
  CONSTRAINT chk_author_status CHECK (author_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 알라딘 categoryId/categoryName은 leaf category에 참고용으로 연결한다 (AladinCategoryImportService 자동 생성).
CREATE TABLE IF NOT EXISTS category (
  category_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_category_id BIGINT UNSIGNED NULL,
  category_name VARCHAR(100) NOT NULL,
  display_order INT UNSIGNED NOT NULL DEFAULT 0,
  category_status VARCHAR(20) NOT NULL,
  aladin_category_id VARCHAR(20) NULL,
  aladin_category_name VARCHAR(255) NULL,
  CONSTRAINT pk_category PRIMARY KEY (category_id),
  CONSTRAINT chk_category_status CHECK (category_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_keyword (
  keyword_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  keyword_name VARCHAR(100) NOT NULL,
  keyword_type VARCHAR(20) NOT NULL,
  keyword_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_book_keyword PRIMARY KEY (keyword_id),
  CONSTRAINT uk_book_keyword_keyword_name UNIQUE (keyword_name),
  CONSTRAINT chk_book_keyword_keyword_type CHECK (keyword_type IN ('GENRE_KEYWORD', 'HASHTAG')),
  CONSTRAINT chk_book_keyword_status CHECK (keyword_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon (
  coupon_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  coupon_name VARCHAR(100) NOT NULL,
  coupon_type VARCHAR(30) NOT NULL,
  benefit_value INT NOT NULL DEFAULT 0,
  benefit_unit VARCHAR(20) NOT NULL,
  valid_days INT UNSIGNED NOT NULL DEFAULT 14,
  coupon_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_coupon PRIMARY KEY (coupon_id),
  CONSTRAINT chk_coupon_benefit_unit CHECK (benefit_unit IN ('AMOUNT', 'PERCENT')),
  CONSTRAINT chk_coupon_status CHECK (coupon_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS challenge (
  challenge_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  challenge_name VARCHAR(100) NOT NULL,
  challenge_type VARCHAR(20) NOT NULL,
  target_action VARCHAR(50) NOT NULL,
  target_count INT UNSIGNED NOT NULL,
  challenge_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_challenge PRIMARY KEY (challenge_id),
  CONSTRAINT chk_challenge_challenge_type CHECK (challenge_type IN ('DAILY', 'TOTAL')),
  CONSTRAINT chk_challenge_status CHECK (challenge_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_request_candidate (
  request_candidate_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  normalized_title VARCHAR(255) NOT NULL,
  normalized_author VARCHAR(100) NOT NULL,
  normalized_publisher VARCHAR(100) NOT NULL,
  request_count INT UNSIGNED NOT NULL DEFAULT 0,
  first_requested_at DATETIME NOT NULL,
  candidate_status VARCHAR(20) NOT NULL,
  CONSTRAINT pk_book_request_candidate PRIMARY KEY (request_candidate_id),
  CONSTRAINT chk_book_request_candidate_candidate_status CHECK (candidate_status IN ('REQUESTED', 'IN_REVIEW', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS library (
  library_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  library_name VARCHAR(100) NOT NULL,
  library_address VARCHAR(255) NOT NULL,
  library_latitude DECIMAL(10,7) NULL,
  library_longitude DECIMAL(10,7) NULL,
  library_status VARCHAR(20) NOT NULL,
  CONSTRAINT pk_library PRIMARY KEY (library_id),
  CONSTRAINT chk_library_status CHECK (library_status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS member (
  member_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  login_id VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  member_name VARCHAR(50) NOT NULL,
  member_email VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NULL,
  birth_date DATE NOT NULL,
  gender VARCHAR(20) NULL,
  member_role VARCHAR(20) NOT NULL,
  grade_id BIGINT UNSIGNED NOT NULL,
  grade_expires_at DATETIME NULL,
  grade_payment_started_at DATETIME NULL,
  point_balance INT NOT NULL DEFAULT 0,
  member_status VARCHAR(20) NOT NULL,
  withdrawn_at DATETIME NULL,
  deactivated_at DATETIME NULL,
  anonymized_at DATETIME NULL,
  withdrawal_reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_member PRIMARY KEY (member_id),
  CONSTRAINT uk_member_login_id UNIQUE (login_id),
  CONSTRAINT uk_member_email UNIQUE (member_email),
  CONSTRAINT chk_member_role CHECK (member_role IN ('USER', 'ADMIN')),
  CONSTRAINT chk_member_point_balance CHECK (point_balance >= 0),
  CONSTRAINT chk_member_status CHECK (member_status IN ('JOINED', 'SANCTIONED', 'WITHDRAWN', 'DEACTIVATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS password_reset_code (
  password_reset_code_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  reset_email VARCHAR(255) NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  reset_code_status VARCHAR(20) NOT NULL,
  fail_count INT UNSIGNED NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  verified_at DATETIME NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_password_reset_code PRIMARY KEY (password_reset_code_id),
  CONSTRAINT chk_password_reset_code_status CHECK (reset_code_status IN ('ISSUED', 'VERIFIED', 'USED', 'EXPIRED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS attendance (
  attendance_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  attendance_date DATE NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_attendance PRIMARY KEY (attendance_id),
  CONSTRAINT uk_attendance_member_id_attendance_date UNIQUE (member_id, attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS point_history (
  point_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  payment_id BIGINT UNSIGNED NULL,
  point_type VARCHAR(30) NOT NULL,
  point_amount INT NOT NULL,
  point_description VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_point_history PRIMARY KEY (point_history_id),
  CONSTRAINT chk_point_history_point_type CHECK (point_type IN ('challenge_reward', 'REVIEW_REWARD', 'PAYBACK', 'EVENT_REWARD', 'PAYMENT_USE', 'ADMIN_ADJUST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book (
  book_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  publisher_id BIGINT UNSIGNED NULL,
  book_title VARCHAR(255) NOT NULL,
  book_isbn CHAR(13) NULL,
  cover_image_url VARCHAR(500) NULL,
  pub_date DATE NULL,
  rental_type VARCHAR(20) NOT NULL,
  rental_price INT NOT NULL DEFAULT 0,
  default_rental_days INT UNSIGNED NOT NULL,
  total_pages INT UNSIGNED NOT NULL DEFAULT 0,
  rental_count INT UNSIGNED NOT NULL DEFAULT 0,
  average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  review_count INT UNSIGNED NOT NULL DEFAULT 0,
  book_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_book PRIMARY KEY (book_id),
  CONSTRAINT uk_book_isbn UNIQUE (book_isbn),
  CONSTRAINT chk_book_rental_type CHECK (rental_type IN ('FREE', 'PAID')),
  CONSTRAINT chk_book_status CHECK (book_status IN ('AVAILABLE', 'HIDDEN', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_author (
  book_id BIGINT UNSIGNED NOT NULL,
  author_id BIGINT UNSIGNED NOT NULL,
  author_order INT UNSIGNED NOT NULL DEFAULT 1,
  CONSTRAINT pk_book_author PRIMARY KEY (book_id, author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_detail (
  book_detail_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  book_description TEXT NULL,
  table_of_contents TEXT NULL,
  publisher_review TEXT NULL,
  detail_content TEXT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_book_detail PRIMARY KEY (book_detail_id),
  CONSTRAINT uk_book_detail_book_id UNIQUE (book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_page (
  book_page_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  book_id BIGINT UNSIGNED NOT NULL,
  page_no INT UNSIGNED NOT NULL,
  page_content LONGTEXT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_book_page PRIMARY KEY (book_page_id),
  CONSTRAINT uk_book_page_book_id_page_no UNIQUE (book_id, page_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_category (
  book_id BIGINT UNSIGNED NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  CONSTRAINT pk_book_category PRIMARY KEY (book_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_keyword_map (
  book_id BIGINT UNSIGNED NOT NULL,
  keyword_id BIGINT UNSIGNED NOT NULL,
  display_order INT UNSIGNED NOT NULL DEFAULT 0,
  CONSTRAINT pk_book_keyword_map PRIMARY KEY (book_id, keyword_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_rental (
  rental_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  rental_start_at DATETIME NULL,
  rental_end_at DATETIME NOT NULL,
  rental_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_book_rental PRIMARY KEY (rental_id),
  CONSTRAINT chk_book_rental_rental_status CHECK (rental_status IN ('READY', 'READING', 'OWNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS reading_progress (
  progress_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rental_id BIGINT UNSIGNED NOT NULL,
  current_page INT UNSIGNED NOT NULL DEFAULT 1,
  read_page_count INT UNSIGNED NOT NULL DEFAULT 0,
  last_read_at DATETIME NULL,
  completed_yn TINYINT(1) NOT NULL DEFAULT 0,
  completed_at DATETIME NULL,
  CONSTRAINT pk_reading_progress PRIMARY KEY (progress_id),
  CONSTRAINT uk_reading_progress_rental_id UNIQUE (rental_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS bookmark (
  bookmark_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rental_id BIGINT UNSIGNED NOT NULL,
  page_no INT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_bookmark PRIMARY KEY (bookmark_id),
  CONSTRAINT uk_bookmark_rental_id_page_no UNIQUE (rental_id, page_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS favorite_book (
  favorite_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_favorite_book PRIMARY KEY (favorite_id),
  CONSTRAINT uk_favorite_book_member_id_book_id UNIQUE (member_id, book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS review (
  review_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  review_content TEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_review PRIMARY KEY (review_id),
  CONSTRAINT uk_review_member_id_book_id UNIQUE (member_id, book_id),
  CONSTRAINT chk_review_status CHECK (review_status IN ('ACTIVE', 'HIDDEN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rating (
  rating_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  rating_score TINYINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_rating PRIMARY KEY (rating_id),
  CONSTRAINT uk_rating_member_id_book_id UNIQUE (member_id, book_id),
  CONSTRAINT chk_rating_rating_score CHECK (rating_score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_report (
  book_report_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reporter_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  report_type VARCHAR(30) NOT NULL,
  book_report_content TEXT NOT NULL,
  report_status VARCHAR(20) NOT NULL,
  processed_at DATETIME NULL,
  process_result TEXT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_book_report PRIMARY KEY (book_report_id),
  CONSTRAINT chk_book_report_status CHECK (report_status IN ('WAITING', 'PROCESSING', 'DONE', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS review_report (
  review_report_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reporter_id BIGINT UNSIGNED NOT NULL,
  review_id BIGINT UNSIGNED NOT NULL,
  report_type VARCHAR(30) NOT NULL,
  review_report_content TEXT NOT NULL,
  report_status VARCHAR(20) NOT NULL,
  processed_at DATETIME NULL,
  process_result TEXT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_review_report PRIMARY KEY (review_report_id),
  CONSTRAINT chk_review_report_status CHECK (report_status IN ('WAITING', 'PROCESSING', 'DONE', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sanction_history (
  sanction_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  review_report_id BIGINT UNSIGNED NULL,
  sanction_type VARCHAR(30) NOT NULL,
  sanction_reason TEXT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_sanction_history PRIMARY KEY (sanction_history_id),
  CONSTRAINT chk_sanction_history_sanction_type CHECK (sanction_type IN ('RENTAL_BAN', 'REVIEW_BAN', 'ACCOUNT_SUSPENSION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment (
  payment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  rental_id BIGINT UNSIGNED NOT NULL,
  payment_type VARCHAR(20) NOT NULL,
  original_amount INT NOT NULL DEFAULT 0,
  payment_amount INT NOT NULL DEFAULT 0,
  payment_method VARCHAR(20) NOT NULL,
  payment_status VARCHAR(20) NOT NULL,
  paid_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_payment PRIMARY KEY (payment_id),
  CONSTRAINT chk_payment_payment_type CHECK (payment_type IN ('RENTAL')),
  CONSTRAINT chk_payment_amount CHECK (payment_amount >= 0),
  CONSTRAINT chk_payment_payment_method CHECK (payment_method IN ('CARD')),
  CONSTRAINT chk_payment_payment_status CHECK (payment_status IN ('PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS member_coupon (
  member_coupon_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  coupon_id BIGINT UNSIGNED NOT NULL,
  coupon_status VARCHAR(20) NOT NULL,
  issued_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  CONSTRAINT pk_member_coupon PRIMARY KEY (member_coupon_id),
  CONSTRAINT chk_member_coupon_coupon_status CHECK (coupon_status IN ('ISSUED', 'USED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_usage (
  coupon_usage_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_coupon_id BIGINT UNSIGNED NOT NULL,
  rental_id BIGINT UNSIGNED NULL,
  payment_id BIGINT UNSIGNED NULL,
  discount_amount INT NOT NULL DEFAULT 0,
  usage_note VARCHAR(255) NULL,
  used_at DATETIME NOT NULL,
  CONSTRAINT pk_coupon_usage PRIMARY KEY (coupon_usage_id),
  CONSTRAINT uk_coupon_usage_member_coupon_id UNIQUE (member_coupon_id),
  CONSTRAINT uk_coupon_usage_payment_id UNIQUE (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS challenge_progress (
  challenge_progress_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  challenge_id BIGINT UNSIGNED NOT NULL,
  progress_date DATE NOT NULL,
  current_count INT UNSIGNED NOT NULL DEFAULT 0,
  achieved_yn TINYINT(1) NOT NULL DEFAULT 0,
  achieved_at DATETIME NULL,
  reward_claimed_at DATETIME NULL,
  CONSTRAINT pk_challenge_progress PRIMARY KEY (challenge_progress_id),
  CONSTRAINT uk_challenge_progress_member_id_challenge_id_progress_date UNIQUE (member_id, challenge_id, progress_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS challenge_reward (
  challenge_reward_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  challenge_id BIGINT UNSIGNED NOT NULL,
  reward_type VARCHAR(20) NOT NULL,
  reward_point INT NULL,
  coupon_id BIGINT UNSIGNED NULL,
  reward_quantity INT UNSIGNED NOT NULL DEFAULT 1,
  CONSTRAINT pk_challenge_reward PRIMARY KEY (challenge_reward_id),
  CONSTRAINT chk_challenge_reward_reward_type CHECK (reward_type IN ('POINT', 'coupon'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS book_request (
  request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_candidate_id BIGINT UNSIGNED NOT NULL,
  member_id BIGINT UNSIGNED NOT NULL,
  approved_book_id BIGINT UNSIGNED NULL,
  request_title VARCHAR(255) NOT NULL,
  request_author VARCHAR(100) NOT NULL,
  request_publisher VARCHAR(100) NOT NULL,
  normalized_title VARCHAR(255) NOT NULL,
  normalized_author VARCHAR(100) NOT NULL,
  normalized_publisher VARCHAR(100) NOT NULL,
  request_reason TEXT NOT NULL,
  request_status VARCHAR(20) NOT NULL,
  processed_at DATETIME NULL,
  reject_reason TEXT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_book_request PRIMARY KEY (request_id),
  CONSTRAINT uk_book_request_member_normalized UNIQUE (member_id, normalized_title, normalized_author, normalized_publisher),
  CONSTRAINT chk_book_request_request_status CHECK (request_status IN ('REQUESTED', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS library_book (
  library_book_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  library_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  holding_status VARCHAR(20) NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_library_book PRIMARY KEY (library_book_id),
  CONSTRAINT uk_library_book_library_id_book_id UNIQUE (library_id, book_id),
  CONSTRAINT chk_library_book_holding_status CHECK (holding_status IN ('AVAILABLE', 'UNAVAILABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS collection (
  collection_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  created_by BIGINT UNSIGNED NULL,
  collection_name VARCHAR(100) NOT NULL,
  collection_type VARCHAR(20) NOT NULL,
  discount_rate DECIMAL(5,2) NULL,
  collection_description TEXT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  collection_status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_collection PRIMARY KEY (collection_id),
  CONSTRAINT chk_collection_collection_type CHECK (collection_type IN ('SERIES', 'EVENT')),
  CONSTRAINT chk_collection_status CHECK (collection_status IN ('ACTIVE', 'HIDDEN', 'ENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS collection_book (
  collection_book_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  collection_id BIGINT UNSIGNED NOT NULL,
  book_id BIGINT UNSIGNED NOT NULL,
  display_order INT UNSIGNED NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_collection_book PRIMARY KEY (collection_book_id),
  CONSTRAINT uk_collection_book_collection_id_book_id UNIQUE (collection_id, book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification (
  notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  notification_type VARCHAR(30) NOT NULL,
  notification_title VARCHAR(100) NOT NULL,
  target_type VARCHAR(30) NULL,
  target_id BIGINT UNSIGNED NULL,
  read_yn TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  read_at DATETIME NULL,
  CONSTRAINT pk_notification PRIMARY KEY (notification_id),
  CONSTRAINT chk_notification_notification_type CHECK (notification_type IN ('RENTAL', 'payment', 'notice', 'EVENT', 'REPORT', 'book_request', 'coupon', 'challenge'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notice (
  notice_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  notice_title VARCHAR(100) NOT NULL,
  notice_content TEXT NOT NULL,
  notice_status VARCHAR(20) NOT NULL,
  notify_yn TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL,
  CONSTRAINT pk_notice PRIMARY KEY (notice_id),
  CONSTRAINT chk_notice_notice_status CHECK (notice_status IN ('ACTIVE', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_reading_statistic (
  user_stat_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  period_type VARCHAR(20) NOT NULL,
  period_start_date DATE NOT NULL,
  period_end_date DATE NOT NULL,
  rental_count INT UNSIGNED NOT NULL DEFAULT 0,
  read_book_count INT UNSIGNED NOT NULL DEFAULT 0,
  read_page_count INT UNSIGNED NOT NULL DEFAULT 0,
  review_count INT UNSIGNED NOT NULL DEFAULT 0,
  favorite_count INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  CONSTRAINT pk_user_reading_statistic PRIMARY KEY (user_stat_id),
  CONSTRAINT uk_user_reading_stat_member_period_start UNIQUE (member_id, period_type, period_start_date),
  CONSTRAINT chk_user_reading_statistic_period_type CHECK (period_type IN ('WEEKLY', 'MONTHLY', 'YEARLY', 'TOTAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS admin_statistic (
  admin_stat_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  period_type VARCHAR(20) NOT NULL,
  age_band VARCHAR(20) NOT NULL DEFAULT 'ALL',
  period_start_date DATE NOT NULL,
  period_end_date DATE NOT NULL,
  total_member_count INT UNSIGNED NOT NULL DEFAULT 0,
  sanctioned_member_count INT UNSIGNED NOT NULL DEFAULT 0,
  active_book_count INT UNSIGNED NOT NULL DEFAULT 0,
  total_rental_count INT UNSIGNED NOT NULL DEFAULT 0,
  total_read_book_count INT UNSIGNED NOT NULL DEFAULT 0,
  free_rental_count INT UNSIGNED NOT NULL DEFAULT 0,
  paid_rental_count INT UNSIGNED NOT NULL DEFAULT 0,
  total_payment_amount BIGINT NOT NULL DEFAULT 0,
  review_count INT UNSIGNED NOT NULL DEFAULT 0,
  report_count INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  CONSTRAINT pk_admin_statistic PRIMARY KEY (admin_stat_id),
  CONSTRAINT uk_admin_statistic_period_type_period_start_date_age_band UNIQUE (period_type, period_start_date, age_band),
  CONSTRAINT chk_admin_statistic_period_type CHECK (period_type IN ('WEEKLY', 'MONTHLY', 'YEARLY', 'TOTAL')),
  CONSTRAINT chk_admin_statistic_age_band CHECK (age_band IN ('ALL', '10S', '20S', '30S', '40S', '50_PLUS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS admin_audit_log (
  audit_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  admin_member_id BIGINT UNSIGNED NULL,
  action_type VARCHAR(50) NOT NULL,
  target_type VARCHAR(50) NOT NULL,
  target_id BIGINT UNSIGNED NULL,
  before_summary TEXT NULL,
  after_summary TEXT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT pk_admin_audit_log PRIMARY KEY (audit_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK 제약 (이미 존재하면 continue-on-error=true 로 무시)
ALTER TABLE member ADD CONSTRAINT fk_member_member_grade FOREIGN KEY (grade_id) REFERENCES member_grade(grade_id);
ALTER TABLE password_reset_code ADD CONSTRAINT fk_password_reset_code_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE attendance ADD CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE point_history ADD CONSTRAINT fk_point_history_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE point_history ADD CONSTRAINT fk_point_history_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id);
ALTER TABLE book ADD CONSTRAINT fk_book_publisher FOREIGN KEY (publisher_id) REFERENCES publisher(publisher_id);
ALTER TABLE book_author ADD CONSTRAINT fk_book_author_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE book_author ADD CONSTRAINT fk_book_author_author FOREIGN KEY (author_id) REFERENCES author(author_id);
ALTER TABLE book_detail ADD CONSTRAINT fk_book_detail_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE book_page ADD CONSTRAINT fk_book_page_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE category ADD CONSTRAINT fk_category_category FOREIGN KEY (parent_category_id) REFERENCES category(category_id);
ALTER TABLE category ADD CONSTRAINT uk_category_aladin_category_id UNIQUE (aladin_category_id);
ALTER TABLE category ADD CONSTRAINT uk_category_parent_name UNIQUE (parent_category_id, category_name);
ALTER TABLE book_category ADD CONSTRAINT fk_book_category_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE book_category ADD CONSTRAINT fk_book_category_category FOREIGN KEY (category_id) REFERENCES category(category_id);
ALTER TABLE book_keyword_map ADD CONSTRAINT fk_book_keyword_map_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE book_keyword_map ADD CONSTRAINT fk_book_keyword_map_book_keyword FOREIGN KEY (keyword_id) REFERENCES book_keyword(keyword_id);
ALTER TABLE book_rental ADD CONSTRAINT fk_book_rental_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE book_rental ADD CONSTRAINT fk_book_rental_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE reading_progress ADD CONSTRAINT fk_reading_progress_book_rental FOREIGN KEY (rental_id) REFERENCES book_rental(rental_id);
ALTER TABLE bookmark ADD CONSTRAINT fk_bookmark_book_rental FOREIGN KEY (rental_id) REFERENCES book_rental(rental_id);
ALTER TABLE favorite_book ADD CONSTRAINT fk_favorite_book_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE favorite_book ADD CONSTRAINT fk_favorite_book_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE review ADD CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE review ADD CONSTRAINT fk_review_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE rating ADD CONSTRAINT fk_rating_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE rating ADD CONSTRAINT fk_rating_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE book_report ADD CONSTRAINT fk_book_report_member FOREIGN KEY (reporter_id) REFERENCES member(member_id);
ALTER TABLE book_report ADD CONSTRAINT fk_book_report_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE review_report ADD CONSTRAINT fk_review_report_member FOREIGN KEY (reporter_id) REFERENCES member(member_id);
ALTER TABLE review_report ADD CONSTRAINT fk_review_report_review FOREIGN KEY (review_id) REFERENCES review(review_id);
ALTER TABLE sanction_history ADD CONSTRAINT fk_sanction_history_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE sanction_history ADD CONSTRAINT fk_sanction_history_review_report FOREIGN KEY (review_report_id) REFERENCES review_report(review_report_id);
ALTER TABLE payment ADD CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE payment ADD CONSTRAINT fk_payment_book_rental FOREIGN KEY (rental_id) REFERENCES book_rental(rental_id);
ALTER TABLE member_coupon ADD CONSTRAINT fk_member_coupon_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE member_coupon ADD CONSTRAINT fk_member_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon(coupon_id);
ALTER TABLE coupon_usage ADD CONSTRAINT fk_coupon_usage_member_coupon FOREIGN KEY (member_coupon_id) REFERENCES member_coupon(member_coupon_id);
ALTER TABLE coupon_usage ADD CONSTRAINT fk_coupon_usage_book_rental FOREIGN KEY (rental_id) REFERENCES book_rental(rental_id);
ALTER TABLE coupon_usage ADD CONSTRAINT fk_coupon_usage_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id);
ALTER TABLE challenge_progress ADD CONSTRAINT fk_challenge_progress_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE challenge_progress ADD CONSTRAINT fk_challenge_progress_challenge FOREIGN KEY (challenge_id) REFERENCES challenge(challenge_id);
ALTER TABLE challenge_reward ADD CONSTRAINT fk_challenge_reward_challenge FOREIGN KEY (challenge_id) REFERENCES challenge(challenge_id);
ALTER TABLE challenge_reward ADD CONSTRAINT fk_challenge_reward_coupon FOREIGN KEY (coupon_id) REFERENCES coupon(coupon_id);
ALTER TABLE book_request ADD CONSTRAINT fk_book_request_book_request_candidate FOREIGN KEY (request_candidate_id) REFERENCES book_request_candidate(request_candidate_id);
ALTER TABLE book_request ADD CONSTRAINT fk_book_request_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE book_request ADD CONSTRAINT fk_book_request_book FOREIGN KEY (approved_book_id) REFERENCES book(book_id);
ALTER TABLE library_book ADD CONSTRAINT fk_library_book_library FOREIGN KEY (library_id) REFERENCES library(library_id);
ALTER TABLE library_book ADD CONSTRAINT fk_library_book_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE collection ADD CONSTRAINT fk_collection_member FOREIGN KEY (created_by) REFERENCES member(member_id);
ALTER TABLE collection_book ADD CONSTRAINT fk_collection_book_collection FOREIGN KEY (collection_id) REFERENCES collection(collection_id);
ALTER TABLE collection_book ADD CONSTRAINT fk_collection_book_book FOREIGN KEY (book_id) REFERENCES book(book_id);
ALTER TABLE notification ADD CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE user_reading_statistic ADD CONSTRAINT fk_user_reading_statistic_member FOREIGN KEY (member_id) REFERENCES member(member_id);
ALTER TABLE admin_audit_log ADD CONSTRAINT fk_admin_audit_log_member FOREIGN KEY (admin_member_id) REFERENCES member(member_id);

CREATE INDEX idx_member_grade_id ON member (grade_id);
CREATE INDEX idx_member_member_status ON member (member_status);
CREATE INDEX idx_password_reset_code_member_id_status_expires_at ON password_reset_code (member_id, reset_code_status, expires_at);
CREATE INDEX idx_password_reset_code_member_id_created_at ON password_reset_code (member_id, created_at);
CREATE INDEX idx_password_reset_code_reset_code_status_created_at ON password_reset_code (reset_code_status, created_at);
CREATE INDEX idx_password_reset_code_created_at ON password_reset_code (created_at);
CREATE INDEX idx_point_history_member_id ON point_history (member_id);
CREATE INDEX idx_point_history_payment_id ON point_history (payment_id);
CREATE INDEX idx_point_history_point_type ON point_history (point_type);
CREATE INDEX idx_point_history_created_at ON point_history (created_at);
CREATE INDEX idx_publisher_publisher_status ON publisher (publisher_status);
CREATE INDEX idx_author_author_name ON author (author_name);
CREATE INDEX idx_author_author_status ON author (author_status);
CREATE INDEX idx_book_book_title ON book (book_title);
CREATE INDEX idx_book_publisher_id ON book (publisher_id);
CREATE INDEX idx_book_rental_type ON book (rental_type);
CREATE INDEX idx_book_book_status ON book (book_status);
CREATE INDEX idx_book_rental_count ON book (rental_count);
CREATE INDEX idx_book_average_rating ON book (average_rating);
CREATE INDEX idx_book_review_count ON book (review_count);
CREATE INDEX idx_book_author_author_id ON book_author (author_id);
CREATE INDEX idx_book_category_category_id ON book_category (category_id);
CREATE INDEX idx_book_keyword_keyword_type ON book_keyword (keyword_type);
CREATE INDEX idx_book_keyword_keyword_status ON book_keyword (keyword_status);
CREATE INDEX idx_book_keyword_map_keyword_id ON book_keyword_map (keyword_id);
CREATE INDEX idx_book_keyword_map_display_order ON book_keyword_map (display_order);
CREATE INDEX idx_book_rental_member_id ON book_rental (member_id);
CREATE INDEX idx_book_rental_book_id ON book_rental (book_id);
CREATE INDEX idx_book_rental_rental_status ON book_rental (rental_status);
CREATE INDEX idx_book_rental_rental_end_at ON book_rental (rental_end_at);
CREATE INDEX idx_reading_progress_completed_yn ON reading_progress (completed_yn);
CREATE INDEX idx_review_book_id ON review (book_id);
CREATE INDEX idx_review_review_status ON review (review_status);
CREATE INDEX idx_rating_book_id ON rating (book_id);
CREATE INDEX idx_book_report_reporter_id ON book_report (reporter_id);
CREATE INDEX idx_book_report_book_id ON book_report (book_id);
CREATE INDEX idx_book_report_report_status ON book_report (report_status);
CREATE INDEX idx_book_report_created_at ON book_report (created_at);
CREATE INDEX idx_review_report_reporter_id ON review_report (reporter_id);
CREATE INDEX idx_review_report_review_id ON review_report (review_id);
CREATE INDEX idx_review_report_report_status ON review_report (report_status);
CREATE INDEX idx_review_report_created_at ON review_report (created_at);
CREATE INDEX idx_sanction_history_member_id ON sanction_history (member_id);
CREATE INDEX idx_sanction_history_review_report_id ON sanction_history (review_report_id);
CREATE INDEX idx_sanction_history_started_at ON sanction_history (started_at);
CREATE INDEX idx_sanction_history_ended_at ON sanction_history (ended_at);
CREATE INDEX idx_payment_member_id ON payment (member_id);
CREATE INDEX idx_payment_rental_id ON payment (rental_id);
CREATE INDEX idx_payment_payment_status ON payment (payment_status);
CREATE INDEX idx_payment_paid_at ON payment (paid_at);
CREATE INDEX idx_member_coupon_member_id ON member_coupon (member_id);
CREATE INDEX idx_member_coupon_coupon_status ON member_coupon (coupon_status);
CREATE INDEX idx_member_coupon_expires_at ON member_coupon (expires_at);
CREATE INDEX idx_coupon_usage_rental_id ON coupon_usage (rental_id);
CREATE INDEX idx_book_request_candidate_normalized ON book_request_candidate (normalized_title, normalized_author, normalized_publisher);
CREATE INDEX idx_book_request_candidate_candidate_status ON book_request_candidate (candidate_status);
CREATE INDEX idx_book_request_request_candidate_id ON book_request (request_candidate_id);
CREATE INDEX idx_book_request_request_status ON book_request (request_status);
CREATE INDEX idx_book_request_created_at ON book_request (created_at);
CREATE INDEX idx_library_book_book_id ON library_book (book_id);
CREATE INDEX idx_library_book_holding_status ON library_book (holding_status);
CREATE INDEX idx_collection_collection_type ON collection (collection_type);
CREATE INDEX idx_collection_collection_status ON collection (collection_status);
CREATE INDEX idx_collection_start_date ON collection (start_date);
CREATE INDEX idx_collection_end_date ON collection (end_date);
CREATE INDEX idx_collection_created_by ON collection (created_by);
CREATE INDEX idx_collection_book_book_id ON collection_book (book_id);
CREATE INDEX idx_collection_book_display_order ON collection_book (display_order);
CREATE INDEX idx_notification_member_id ON notification (member_id);
CREATE INDEX idx_notification_read_yn ON notification (read_yn);
CREATE INDEX idx_notification_created_at ON notification (created_at);
CREATE INDEX idx_notification_notification_type ON notification (notification_type);
CREATE INDEX idx_admin_audit_log_admin_member_id ON admin_audit_log (admin_member_id);
CREATE INDEX idx_admin_audit_log_action_type ON admin_audit_log (action_type);
CREATE INDEX idx_admin_audit_log_target_type ON admin_audit_log (target_type);
CREATE INDEX idx_admin_audit_log_target_id ON admin_audit_log (target_id);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log (created_at);
