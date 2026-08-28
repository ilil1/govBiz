# GovBiz Core API

Spring Boot 기반의 GovBiz 브라우저 공개 API입니다. React의 업무 요청을 받고, 공공데이터포털의
기업마당 공고와 내부 AI Service 호출 결과를 안정적인 공개 HTTP 계약으로 변환합니다.

## 현재 API

| Endpoint | 용도 |
|---|---|
| `GET /api/v1/health` | Core API 자체 상태 |
| `GET /api/v1/health/ai-service` | Core API를 통한 AI Service 상태 |
| `GET /api/v1/support-programs/search?query=수출&acceptingOnly=true` | 기업마당 실제 공고 검색 |
| `POST /api/v1/sample-items/prepare` | 예제 수직 슬라이스의 입력 검증과 준비 상태 반환 |

지원사업 검색의 요청·응답 필드, 날짜·상태 의미와 오류 코드는
[지원사업 검색 HTTP 계약](../../docs/support-program-search-contract.md)을 참고하세요. 검색 결과는
관련도와 갱신시각 순으로 최대 5개를 반환합니다.

`SampleItem`의 정확한 요청·응답은 [SampleItem 계약](../../docs/sample-item-contract.md)을
참고하세요.

현재 SampleItem은 실제 지원사업 검색과 무관한 계층 연결 구조 예제입니다.

## 구조

```text
controller/      # HTTP 요청·응답과 application/problem+json 변환
service/         # 검색·정렬·날짜 판정·캐시와 use case
domain/support/  # 지원사업 모델과 상태
dto/support/     # 브라우저 공개 검색 DTO
client/bizinfo/  # 공공데이터포털 전송 DTO와 HTTP Client
client/ai/       # FastAPI 내부 HTTP Client
config/          # CORS, HTTP Client, Clock, JSON 설정
```

Kotlin 기본 패키지는 `ai.govbiz.core`이고 Gradle 프로젝트명은 `govbiz-core-api`입니다.

외부 HTTP 호출은 영속성 Repository가 아니므로 소스별 `client`에 둡니다. Bizinfo client는 인증키와
공공데이터포털 전송 계약만 소유하고, Service가 HTML 제거, 공식 원문 URL 검증, 신청기간·접수상태
계산, 검색과 정렬을 담당합니다. 데이터 저장이 필요한 기능이 생길 때 실제 Repository를 추가하세요.

## 실행

JDK 21이 필요합니다.

```bash
./gradlew bootRun
```

기본 주소는 `http://127.0.0.1:8080`입니다. 실제 공고 검색에는 공공데이터포털에서 발급한 개인
서비스키가 필요합니다. Encoding 또는 Decoding 키를 `DATA_GO_KR_SERVICE_KEY`에 넣을 수 있으며,
Core API가 외부 요청 전에 정확히 한 번 인코딩합니다. 키를 `application.properties`, Git 또는
Frontend 환경변수에 기록하지 마세요.

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `DATA_GO_KR_SERVICE_KEY` | 빈 값 | 공공데이터포털 인증키. 비어 있으면 검색만 503 |
| `BIZINFO_API_BASE_URL` | `https://apis.data.go.kr` | 공고 API origin |
| `BIZINFO_API_CONNECT_TIMEOUT` | `2s` | 공고 API 연결 제한시간 |
| `BIZINFO_API_READ_TIMEOUT` | `10s` | 공고 API 응답 제한시간 |
| `AI_SERVICE_BASE_URL` | `http://127.0.0.1:8000` | 내부 AI Service 주소 |
| `AI_SERVICE_CONNECT_TIMEOUT` | `1s` | AI Service 연결 제한시간 |
| `AI_SERVICE_READ_TIMEOUT` | `3s` | AI Service 응답 제한시간(LLM 기본 제한 2.5초 + 내부 응답 여유) |
| `APP_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | 허용할 Web origin |

Compose 실행은 저장소 루트 `.env`의 `DATA_GO_KR_SERVICE_KEY`를 Core API에, `OPENAI_API_KEY`를 AI
Service에만 전달합니다. 네이티브 실행에서는 각 프로세스 환경변수를 직접 설정해야 합니다.

## 지원사업 검색 동작

Core API는 공공데이터포털 응답을 한 시간 동안 메모리에 캐시합니다. 갱신 호출이 실패해도 직전
데이터가 24시간 이내라면 stale cache로 검색을 이어가고, 사용 가능한 cache가 없을 때만 안전한
공개 오류를 반환합니다. 검색은 서울 시간 기준으로 접수상태를 계산합니다.

| 상황 | HTTP | code |
|---|---:|---|
| 인증키 미설정 | 503 | `SUPPORT_PROGRAM_SOURCE_NOT_CONFIGURED` |
| 외부 API 실패 응답 | 502 | `SUPPORT_PROGRAM_SOURCE_ERROR` |
| 잘못된 외부 JSON·필드 구조 | 502 | `SUPPORT_PROGRAM_INVALID_RESPONSE` |
| 연결 불가 | 503 | `SUPPORT_PROGRAM_SOURCE_UNAVAILABLE` |
| 연결·읽기 시간 초과 | 504 | `SUPPORT_PROGRAM_SOURCE_TIMEOUT` |

외부 URL, 인증키와 라이브러리 예외는 공개 응답에 노출하지 않습니다.

## AI Service 오류 계약

| 상황 | HTTP | code |
|---|---:|---|
| AI Service/OpenAI 실패 응답 | 502 | `AI_SERVICE_UPSTREAM_ERROR` |
| 잘못된 Content-Type·JSON·응답 body | 502 | `AI_SERVICE_INVALID_RESPONSE` |
| 연결 불가 | 503 | `AI_SERVICE_UNAVAILABLE` |
| 연결·읽기 시간 초과 | 504 | `AI_SERVICE_TIMEOUT` |

내부 URL과 라이브러리 예외는 공개 응답에 노출하지 않습니다.

## 검증

```bash
./gradlew clean build --no-daemon
```
