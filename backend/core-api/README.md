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
[지원사업 검색·추천 HTTP 계약](../../docs/support-program-search-contract.md)을 참고하세요. 검색 결과는
LLM 총점 순으로 최대 5개를 반환합니다.

`SampleItem`의 정확한 요청·응답은 [SampleItem 계약](../../docs/sample-item-contract.md)을
참고하세요.

현재 SampleItem은 실제 지원사업 검색과 무관한 계층 연결 구조 예제입니다.

## 구조

```text
supportprogram/
├── controller/             # 지원사업 공개 HTTP API
├── service/                # 검색 흐름, 공고 정규화와 AI 결과 검증
├── domain/                 # 지원사업 모델과 상태
├── dto/                    # 브라우저 공개 응답 DTO
└── client/
    ├── ai/                 # AI 점수화 내부 요청·응답 계약
    └── bizinfo/            # 기업마당 Client와 연결 설정
aiservice/
├── controller/             # AI Service Health 공개 API
├── service/                # Health 계약 검증
├── dto/                    # Health 공개 응답 DTO
├── client/                 # FastAPI HTTP Client
└── config/                 # FastAPI 주소와 HTTP 설정
sampleitem/
├── controller/             # SampleItem 예제 공개 API
├── service/                # SampleItem 준비 흐름
├── domain/                 # SampleItem 모델과 상태
└── dto/                    # SampleItem 요청·응답 DTO
health/
├── controller/             # Core API 자체 Health API
└── dto/                    # Core Health 응답 DTO
_common/
├── config/                 # CORS, JSON, 공통 RestClient 생성 설정
├── exception/              # 모든 기능이 공유하는 ProblemDetail 예외 처리
└── http/                   # 외부 Client가 공유하는 timeout 판별
```

Kotlin 기본 패키지는 `ai.govbiz.core`이고 Gradle 프로젝트명은 `govbiz-core-api`입니다.

지원사업 검색 관련 코드는 `supportprogram` 기능 디렉터리에서 함께 관리합니다. 기능 안의 `controller → service → domain` 흐름과 `client/ai`, `client/bizinfo` 외부 연결을 한곳에서 따라갈 수 있습니다. BizInfo Client는 인증키와 공공데이터포털 전송 계약을 소유하고, Service가 HTML 제거·공식 원문 URL 검증·신청기간과 접수상태 계산을 담당합니다. AI 점수화 Service는 후보를 FastAPI에 보내고 ID·점수 합계·내림차순을 재검증합니다. Kotlin 단어 사전과 고정 관련도 가중치는 사용하지 않습니다.

계층 연결 예제인 SampleItem도 `sampleitem/controller → service → domain`으로 독립되어 있으며 공개 요청·응답 형식은 `sampleitem/dto`가 소유합니다.

Core API 프로세스 자체의 생존 상태는 `health/controller`와 `health/dto`가 담당합니다. AI Service 연결 상태를 확인하는 `aiservice` 기능과는 별개입니다.

둘 이상의 기능이 실제로 함께 사용하는 코드만 `_common`에 둡니다. 앞의 밑줄은 IDE의 알파벳 정렬에서 공통 코드를 기능보다 위에 표시하려는 프로젝트 규칙입니다. `_common/config`는 전체 API의 JSON·CORS 정책과 외부 HTTP Client 공통 생성을 담당합니다. `ApiExceptionHandler`는 기능별 예외를 동일한 ProblemDetail 형식으로 변환하고, `TimeoutCause`는 AI·기업마당 HTTP Client가 timeout 원인을 같은 방식으로 판별합니다.

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
| `AI_SERVICE_READ_TIMEOUT` | `12s` | AI Service 응답 제한시간(LLM 전체 제한 10초 + 내부 응답 여유) |
| `APP_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | 허용할 Web origin |

Compose 실행은 저장소 루트 `.env`의 `DATA_GO_KR_SERVICE_KEY`를 Core API에, `OPENAI_API_KEY`를 AI
Service에만 전달합니다. 네이티브 실행에서는 각 프로세스 환경변수를 직접 설정해야 합니다.

## 지원사업 검색 동작

Core API는 현재 검색 요청마다 공공데이터포털을 조회하며 메모리 캐시를 사용하지 않습니다. 외부
호출 실패는 즉시 안전한 공개 오류로 반환합니다. 검색은 서울 시간 기준으로 접수상태를 계산하고,
접수 필터 적용 후 최신 후보 최대 20개를 AI Service에 보냅니다. LLM이 버전된 100점 기준으로 모든
후보를 점수화하며 Core는 결과를 방어적으로 검증합니다.
현재 최신 20개 제한은 벡터 검색 전의 임시 후보 선택입니다.
다음 단계에서는 정기 수집과 DB 검색으로 외부 호출을 사용자 요청 경로에서 제거합니다.

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
