# Base Architecture Core API

Spring Boot 기반의 브라우저 공개 API입니다. React의 업무 요청을 받고, 내부 AI Service 호출 결과를
안정적인 공개 HTTP 계약으로 변환합니다.

## 현재 API

| Endpoint | 용도 |
|---|---|
| `GET /api/v1/health` | Core API 자체 상태 |
| `GET /api/v1/health/ai-service` | Core API를 통한 AI Service 상태 |
| `POST /api/v1/sample-items/prepare` | 예제 수직 슬라이스의 입력 검증과 준비 상태 반환 |

`SampleItem`의 정확한 요청·응답은 [SampleItem 계약](../../docs/sample-item-contract.md)을
참고하세요.

## 구조

```text
controller/   # HTTP DTO, 요청 검증, 응답 변환
service/      # use case와 상태 전이
domain/       # 프레임워크에 독립적인 record·enum·불변식
client/ai/    # FastAPI 내부 HTTP Client
config/       # CORS, HTTP Client, JSON 설정
```

외부 HTTP 호출은 영속성 Repository가 아니므로 `client/ai`에 둡니다. 데이터 저장이 필요한 기능이
생길 때 실제 Repository를 추가하세요.

## 실행

Java 21이 필요합니다.

```bash
./gradlew bootRun
```

기본 주소는 `http://127.0.0.1:8080`입니다. AI Service의 기본 주소는
`http://127.0.0.1:8000`이며, 아래 환경변수로 바꿀 수 있습니다.

```text
AI_SERVICE_BASE_URL
AI_SERVICE_CONNECT_TIMEOUT
AI_SERVICE_READ_TIMEOUT
APP_CORS_ALLOWED_ORIGIN
```

## AI Service 오류 계약

| 상황 | HTTP | code |
|---|---:|---|
| 예상하지 않은 upstream 상태 | 502 | `AI_SERVICE_UPSTREAM_ERROR` |
| 잘못된 Content-Type·JSON·응답 body | 502 | `AI_SERVICE_INVALID_RESPONSE` |
| 연결 불가 | 503 | `AI_SERVICE_UNAVAILABLE` |
| 연결·읽기 시간 초과 | 504 | `AI_SERVICE_TIMEOUT` |

내부 URL과 라이브러리 예외는 공개 응답에 노출하지 않습니다.

## 검증

```bash
./gradlew clean build --no-daemon
```
