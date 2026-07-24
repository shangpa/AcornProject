# Google 로그인 Backend Production Runbook

## 0. 대상과 기준

| 항목 | 값 |
|---|---|
| Release SHA | `017ab6d324239f2af0edcafa78598ee4bd0ecae0` |
| PR | [#70](https://github.com/shangpa/AcornProject/pull/70) |
| Release 직전 production SHA | `30d1c30332356e94dc20bfe3e418e1bff80f0099` |
| Migration | `src/main/resources/db/manual/V004__oauth_account.sql` |
| Migration Git blob | `bdf9e3ae7fade4c8b222f3d2f09c4147786a0041` |
| Feature flag | `GOOGLE_AUTH_ENABLED` (기본 `false`) |

일반 배포 절차는 [`CI-CD_개발자_사용가이드.md`](CI-CD_개발자_사용가이드.md)를 따른다. 이 문서는 그 위에서
Google 로그인 release에만 필요한 DB migration과 feature flag rollout 순서를 다룬다.

DB 작업 전 [`docs/데이터베이스/README.md`](../데이터베이스/README.md)를 완전히 읽는다.
V004의 분류·상태 판별·멱등성·postflight·rollback 규칙은 그 문서의 "V004 실행 규칙"에 있다.

## 1. 선행 조건 (가장 중요)

**`GOOGLE_AUTH_ENABLED=false`여도 migration은 반드시 먼저 적용해야 한다.**

`OauthAccount` entity가 존재하므로 `spring.jpa.hibernate.ddl-auto=validate`가 `oauth_account` table을
요구한다. table 없이 새 image를 배포하면 feature flag와 무관하게 container가 기동에 실패하고,
CD의 `/api/v1/categories` smoke가 깨진다.

`release-contract.yml`의 `requiredMigrations`가 비어 있지 않으므로
[`backend-cd.yml`](../../.github/workflows/backend-cd.yml)의 gate가 `migration_evidence` 없이는
CD job을 시작하지 않는다. 이 gate를 우회하지 않는다.

## 2. Migration 실행 host

RDS가 private subnet이면 개발자 로컬이나 CloudShell에서 직접 접속할 수 없다.
**RDS에 network 접근이 가능한 EC2를 SSM Session Manager로 사용한다.**

```bash
aws ssm start-session --target <EC2_INSTANCE_ID>
```

SSM Run Command가 아니라 Session Manager를 쓴다. Run Command의 인자와 출력은
command history 및 CloudWatch에 영구 기록되므로 DB 접속 정보가 남으면 안 된다
([`CI-CD_구축계획.md`](CI-CD_구축계획.md) 5-3).

## 3. V004 파일 준비와 무결성 검증

**production EC2의 source는 구 SHA(`30d1c30`)일 수 있고, 그 시점에는 V004 파일이 존재하지 않는다.**
EC2에 파일이 있다고 가정하지 않는다.

```bash
ls -l src/main/resources/db/manual/V004__oauth_account.sql
```

없으면 최신 main에서 추출한다. **SQL을 임의로 재작성하거나 손으로 옮겨 적지 않는다.**

repo가 있는 host:

```bash
git fetch origin
git show 017ab6d324239f2af0edcafa78598ee4bd0ecae0:src/main/resources/db/manual/V004__oauth_account.sql \
  > /tmp/V004__oauth_account.sql
```

repo가 없어 파일을 전송했다면 실행 전에 반드시 무결성을 확인한다.
아래 값과 정확히 일치하지 않으면 **실행하지 않는다.**

```bash
git hash-object /tmp/V004__oauth_account.sql
# 기대값: bdf9e3ae7fade4c8b222f3d2f09c4147786a0041
```

## 4. 상태 판별 (read-only)

```sql
SELECT VERSION() AS mysql_version, DATABASE() AS target_schema;

SELECT EXISTS (
  SELECT 1 FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oauth_account'
) AS has_oauth_account;
```

`has_oauth_account = 0`이면 미적용이므로 5절로 간다.

`has_oauth_account = 1`이어도 **table 존재나 column 개수만으로 "적용 완료"로 판정하지 않는다.**
column이 7개여도 FK나 UNIQUE가 빠져 있으면 부분 적용 상태이고, 그대로 배포하면
같은 Google 계정이 여러 회원에 연결되는 것을 DB가 막지 못한다.
아래 4가지를 모두 확인한 뒤에만 "already applied"로 판정한다.

### 4-1. 전체 구조 (사람이 읽는 확인)

```sql
SHOW CREATE TABLE oauth_account;
```

### 4-2. Column 계약

```sql
SELECT
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oauth_account') AS total_columns,
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oauth_account'
     AND (
       (COLUMN_NAME = 'oauth_account_id' AND DATA_TYPE = 'bigint' AND COLUMN_TYPE LIKE '%unsigned%'
         AND IS_NULLABLE = 'NO' AND EXTRA LIKE '%auto_increment%')
       OR (COLUMN_NAME = 'member_id' AND DATA_TYPE = 'bigint' AND COLUMN_TYPE LIKE '%unsigned%'
         AND IS_NULLABLE = 'NO')
       OR (COLUMN_NAME = 'provider' AND DATA_TYPE = 'varchar'
         AND CHARACTER_MAXIMUM_LENGTH = 20 AND IS_NULLABLE = 'NO')
       OR (COLUMN_NAME = 'provider_user_id' AND DATA_TYPE = 'varchar'
         AND CHARACTER_MAXIMUM_LENGTH = 255 AND IS_NULLABLE = 'NO')
       OR (COLUMN_NAME = 'provider_email' AND DATA_TYPE = 'varchar'
         AND CHARACTER_MAXIMUM_LENGTH = 255 AND IS_NULLABLE = 'YES')
       OR (COLUMN_NAME = 'created_at' AND DATA_TYPE = 'datetime' AND IS_NULLABLE = 'NO')
       OR (COLUMN_NAME = 'updated_at' AND DATA_TYPE = 'datetime' AND IS_NULLABLE = 'YES')
     )) AS matching_columns;
```

기대: `total_columns = 7`, `matching_columns = 7`

### 4-3. Index 이름·개수·컬럼 순서

```sql
SELECT INDEX_NAME, NON_UNIQUE,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS index_columns
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oauth_account'
GROUP BY INDEX_NAME, NON_UNIQUE
ORDER BY INDEX_NAME;
```

기대: **정확히 3행**

| INDEX_NAME | NON_UNIQUE | index_columns |
|---|---|---|
| `PRIMARY` | 0 | `oauth_account_id` |
| `uk_oauth_account_member_id_provider` | 0 | `member_id,provider` |
| `uk_oauth_account_provider_provider_user_id` | 0 | `provider,provider_user_id` |

FK 전용 index가 따로 생기지 않는 것이 정상이다. `uk_oauth_account_member_id_provider`가
`member_id`로 시작해 FK 요구를 충족하므로 MySQL이 추가 index를 만들지 않는다.
4행 이상이면 기대 구조가 아니므로 `MIXED`로 판정한다.

### 4-4. FK와 CHECK

```sql
SELECT rc.CONSTRAINT_NAME, rc.REFERENCED_TABLE_NAME,
       GROUP_CONCAT(kcu.COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION) AS fk_columns,
       GROUP_CONCAT(kcu.REFERENCED_COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION) AS referenced_columns
FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
  ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
 AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
WHERE rc.CONSTRAINT_SCHEMA = DATABASE() AND rc.TABLE_NAME = 'oauth_account'
GROUP BY rc.CONSTRAINT_NAME, rc.REFERENCED_TABLE_NAME;

SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_oauth_account_provider';
```

기대:

- FK 1행 — `fk_oauth_account_member | member | member_id | member_id`
- CHECK 1행 — `chk_oauth_account_provider`

### 4-5. 판정

| 조건 | 판정 | 조치 |
|---|---|---|
| table 없음 | 미적용 | V004 실행 |
| 4-2, 4-3, 4-4가 **전부** 일치 | 적용 완료 | 실행 생략 가능(재실행해도 무해) |
| **하나라도 불일치** (column 7개인데 FK/UNIQUE 누락 포함) | `MIXED` | **중단.** DDL을 실행하지 말고 `SHOW CREATE TABLE` 출력과 함께 보고 |

## 5. Snapshot 후 실행

```bash
# 1) RDS snapshot을 먼저 만들고 snapshot-id를 기록한다.
# 2) DELIMITER와 stored procedure를 쓰므로 반드시 mysql CLI로 실행한다.
mysql -h <rds-endpoint> -u <user> -p <database> < /tmp/V004__oauth_account.sql
echo "exit=$?"
```

`exit`이 `0`이 아니면 구조가 기대와 다른 것이다. **추측으로 고치지 말고 출력을 보고한다.**
V004는 부분 적용 상태를 감지하면 `SIGNAL SQLSTATE '45000'`으로 **DDL을 하나도 적용하지 않고** 중단한다.

성공하면 마지막에 column 7행, index 3행, row count가 출력된다. 이것이 postflight 통과 증거다.

## 6. Migration evidence 형식

CD의 `migration_evidence` 입력에 아래 형식으로 기록한다.

```
rds-snapshot=<snapshot-id>; V004=<applied-at ISO8601>; postflight=<result>
```

적용한 경우:

```
rds-snapshot=abc-prod-20260724-pre-v004; V004=2026-07-24T18:20:00+09:00; postflight=exit0-columns7-indexes3-fk1
```

이미 적용돼 있어 4절 검증만 수행하고 실행을 생략한 경우:

```
rds-snapshot=abc-prod-20260724-pre-v004; V004=already-applied-verified-2026-07-24T18:20:00+09:00; postflight=exit0-columns7-indexes3-fk1
```

## 7. Feature flag rollout 순서

각 단계가 통과해야 다음으로 간다.

| # | 단계 | 통과 기준 |
|---|---|---|
| 1 | RDS snapshot | snapshot-id 확보, 복원 가능성 확인 |
| 2 | V004 migration + postflight | exit 0, 4-2/4-3/4-4 전부 일치 |
| 3 | `.env.prod`에 `GOOGLE_CLIENT_ID` 등록 | Google Console Web client ID |
| 4 | `.env.prod`에 `GOOGLE_AUTH_ENABLED=false` 등록 | 명시적으로 `false` |
| 5 | Backend Production CD 실행 | 8절 입력표 |
| 6 | 기존 login / `/api/v1/categories` smoke | 회귀 없음 |
| 7 | `POST /api/v1/auth/google` + dummy token | **503 `AUTH_GOOGLE_DISABLED`** |
| 8 | `GOOGLE_AUTH_ENABLED=true` 변경 + compose 재기동 | 승인된 SSM 절차 |
| 9 | 같은 dummy token 재요청 | **401 `AUTH_GOOGLE_TOKEN_INVALID`** |
| 10 | 실제 Google 로그인 E2E | **frontend 배포 후** 수행 |

`GOOGLE_CLIENT_ID`는 frontend bundle에도 공개되는 client ID이며 secret이 아니다.
이 기능은 client secret을 사용하지 않으므로 등록하거나 저장하지 않는다.

### 7-1. 7단계와 9단계가 rollout의 핵심 검증

같은 요청의 응답이 **503 -> 401**로 바뀌는 것이 flag가 실제로 전환됐다는 증거다.

```bash
curl -i -X POST https://api.portfoliodev.click/api/v1/auth/google \
  -H 'Content-Type: application/json' \
  -d '{"idToken":"not-a-real-token"}'
```

- 형식 자체가 JWT가 아니므로 Google JWKS를 호출하지 않고 거부된다. 실제 Google 계정이 필요 없다.
- 7단계에서 401이 나오면 flag가 이미 켜져 있는 것이다. `.env.prod`를 다시 확인한다.
- 9단계에서 503이 나오면 재기동이 적용되지 않은 것이다. flag 변경은 container 재기동이 필요하다.

## 8. Backend Production CD 입력

| 입력 | 값 |
|---|---|
| `confirm_production` | `true` |
| `confirm_release_sha` | `017ab6d324239f2af0edcafa78598ee4bd0ecae0` |
| `resume_metadata_only` | `false` |
| `migration_evidence` | 6절 형식 문자열 |
| `bootstrap_current_git_sha` | 공백 |
| `bootstrap_current_image_digest` | 공백 |

배포 후 확인 항목은 [`CI-CD_개발자_사용가이드.md`](CI-CD_개발자_사용가이드.md) 12절 체크리스트를 따른다.

## 9. Rollback

우선순위 순서다.

1. **먼저 `GOOGLE_AUTH_ENABLED=false` + 재기동.** 이 기능에서 발생하는 문제는 대부분 여기서 차단된다.
   image rollback보다 이 경로를 우선한다.
2. `oauth_account`는 additive다. code rollback 시 **drop하지 않는다.** 구버전 image는 이 table을
   참조하지 않으므로 기존 로그인과 `ddl-auto=validate`가 깨지지 않는다.
3. image rollback은 DB/EPUB 호환성 검토 후 별도 승인된 수동 SSM 절차로만 수행한다.
   Backend 자동 rollback은 구현돼 있지 않다.
4. Google 전용 회원이 생성된 뒤의 구조 rollback은 해당 회원의 로그인 수단을 없앤다.
   data-loss 판단과 명시 승인이 선행돼야 한다.

## 10. 배포 후 후속 작업

production이 `017ab6d` 이상으로 배포되면 `release-contract.yml`을 다음 값으로 되돌리는 PR을 만든다.

```yaml
schemaChange: none
requiredMigrations: []
runtimeContractVersion: 1
epubContractChange: false
```

**배포 전에는 되돌리지 않는다.** 되돌리면 CD의 migration gate가 사라져 migration 미적용 상태로
배포될 수 있고, 실제로는 `deploy/db/verify-schema-contract.sh`가 현재 production SHA 기준 diff와
contract 불일치를 감지해 CD가 실패한다.

또한 배포 전까지는 main을 base로 하는 **모든 PR의 Backend CI가 실패한다.** contract가
`additive` + `V004`를 선언하는데 그 PR의 migration diff는 비어 있기 때문이다
(`requiredMigrations does not match added/modified forward migrations`).
따라서 이 release 기간에는 위 초기화 PR을 먼저 처리한 뒤 다른 PR을 진행한다.

## 11. 남은 위험

| 위험 | 내용 |
|---|---|
| login CSRF / ID token replay | backend nonce/challenge가 이번 범위에서 제외됐다. 유출된 Google ID token을 유효기간(약 1시간) 동안 재생할 수 있다 |
| auth endpoint rate limit 부재 | `/api/v1/auth/google/link`가 특정 회원을 겨냥한 비밀번호 추측 창구가 된다. 기존 `/api/v1/auth/login`도 동일하게 무제한이라 회귀는 아니지만 대상이 특정된다는 점이 다르다 |
| `jwt.secret` dev fallback | `application.properties`에 기본값이 있다. prod profile은 기본값이 없어 안전하다. 제거하려면 CI가 `JWT_SECRET`을 설정하도록 함께 고쳐야 한다 |
| transition token 단일 사용 | 소비된 signup token 재사용은 409로 차단하지만 서버측 jti 저장소는 없다. 단일 instance 전제에서만 추가 의미가 있어 도입하지 않았다 |
