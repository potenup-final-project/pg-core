# pg-core

`pg-core`는 결제 도메인의 온라인 트랜잭션을 처리하는 핵심 API 서버입니다.  
역할은 크게 **결제 상태 전이(Claim/Confirm/Cancel)**, **멱등성 보장**, **Outbox 기반 이벤트 발행**, **Webhook 엔드포인트 관리**입니다.

## 1. 기술 스택

- Kotlin, Spring Boot 3.5.x
- Java 17
- Spring Web, Validation, Data JPA, JDBC, AOP, Actuator
- QueryDSL
- MySQL, Redis
- Resilience4j(CircuitBreaker/Retry/Bulkhead)
- AWS SQS SDK
- OpenAPI(springdoc)
- `com.gop.logging:gop-logging-spring-starter`

## 2. 아키텍처 요약

`core`, `webhook`, `global` 세 레이어로 구성되어 있습니다.

- `core`
  - 결제 도메인 모델(`Payment`, `PaymentTransaction`)
  - 유스케이스(`Claim/Confirm/Cancel`)
  - 외부 카드사 게이트웨이 포트/어댑터
  - Outbox Relay, 배치/대사 보정
- `webhook`
  - 상점별 webhook endpoint 등록/수정/조회
  - endpoint 활성 상태 기반 delivery 생성
- `global`
  - 예외 처리, 공통 설정, 로깅 컨텍스트

핵심 포인트는 다음입니다.

- 트랜잭션 분리(외부 연동 구간과 DB 반영 구간 분리)
- 멱등성 필터 + 서비스 레벨 재검증 이중 방어
- Outbox 패턴으로 API 트랜잭션과 비동기 발행 결합 해제
- Resilience4j로 외부 연동 장애를 도메인 예외로 승격

## 3. 주요 흐름

## 3.1 결제 Claim
- merchantId + orderId 기준 선조회
- 기존 건이 있으면 멱등 검증 후 기존 응답 재사용
- 신규 건은 TTL(만료시간) 포함 생성 후 저장
- UNIQUE 충돌 시 재조회 후 동일 정책 적용

## 3.2 결제 Confirm
- 원장 검증 -> 상태 선점(TX-1) -> 외부 승인 호출(TX 밖) -> 결과 반영(TX-2)
- 외부 호출 실패/회로 차단 시 롤백 또는 UNKNOWN 처리
- 승인 성공/실패 메트릭 기록

## 3.3 결제 Cancel
- 원거래 승인 이력 조회 -> 취소 요청 이력 생성 -> 외부 취소 호출 -> 결과 반영
- 재처리 필요 케이스는 후속 배치에서 보정

## 3.4 멱등성
- `IdempotencyFilter`가 Confirm/Cancel POST 요청 대상
- `Idempotency-Key` 필수
- Redis `SETNX` 기반 최초 요청 판별
- 동일 Key + 동일 Body는 캐시 응답 반환, Body 불일치 시 예외

## 3.5 Outbox Relay
- due 이벤트를 claim 후 publish
- 성공/실패/재시도/DEAD 상태를 정책 기반으로 반영
- retry 횟수, 다음 재시도 시각, backlog age 메트릭 관리

## 4. API 엔드포인트(핵심)

### 결제
- `POST /v1/payments/claim`
- `POST /v1/payments/{paymentKey}/confirm`
- `POST /v1/payments/{paymentKey}/cancel`
- `POST /v1/billing-keys`

### Webhook Endpoint 관리
- `POST /v1/merchants/{merchantId}/webhook-endpoints`
- `PATCH /v1/merchants/{merchantId}/webhook-endpoints/{endpointId}`
- `GET /v1/merchants/{merchantId}/webhook-endpoints`

## 5. 스케줄 작업

- `OutboxRelayWorker`: relay 주기 실행
- `OutboxLeaseSweeper`: lease 회수
- `NetCancelBatchJob`, `ReconcileCancelBatchJob`: 취소 보정
- `UnknownPaymentReconciliationWorker`: UNKNOWN 결제 상태 보정

## 6. 실행 방법

## 6.1 사전 준비
- JDK 17
- MySQL, Redis
- GitHub Packages 접근 정보(사설 패키지 의존성 다운로드용)

## 6.2 필수 환경변수(핵심)

빌드 시
- `GOP_LOGGING_VERSION`
- `GITHUB_PACKAGES_URL`
- `GITHUB_PACKAGES_USER`
- `GITHUB_PACKAGES_TOKEN`

실행 시
- `LOG_SERVICE_NAME=pg_core`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `OUTBOX_RELAY_ENABLED`, `OUTBOX_RELAY_PUBLISHER`
- `OUTBOX_RELAY_SQS_WEBHOOK_QUEUE_URL`, `OUTBOX_RELAY_SQS_SETTLEMENT_QUEUE_URL`

## 6.3 로컬 실행

```bash
./gradlew clean bootRun
```

## 6.4 테스트

```bash
./gradlew test
```

## 7. 도커

```bash
./gradlew clean bootJar

docker build -t pg-core:local .
docker run --rm -p 8080:8080 --env-file .env pg-core:local
```

기본 포트는 `8080`, 헬스체크 경로는 `/actuator/health`입니다.

## 8. CI/CD

- `.github/workflows/reusable-build-and-push.yml`
  - `bootJar` 빌드 후 Docker image push
- `.github/workflows/deploy.yml`
  - self-hosted runner에서 컨테이너 교체 배포
  - 배포 후 헬스체크 수행

