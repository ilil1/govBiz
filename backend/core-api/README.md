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
├── dto/
│   ├── api/                # 브라우저 공개 응답 DTO
│   ├── ai/                 # AI Service 내부 요청·응답 DTO
│   └── bizinfo/            # 기업마당 응답 DTO
└── client/
    ├── ai/                 # AI 점수화 Client 계약과 HTTP 구현
    └── bizinfo/            # 기업마당 Client와 연결 설정
_sampleitem/
├── controller/             # SampleItem 예제 공개 API
├── service/                # SampleItem 준비 흐름
├── domain/                 # SampleItem 모델과 상태
└── dto/                    # SampleItem 요청·응답 DTO
_health/
├── controller/             # Core API 자체 Health API
└── dto/                    # Core Health 응답 DTO
_health_ai_service/
├── client/                 # AI Service 내부 Health HTTP 호출
├── controller/             # AI Service Health 공개 API
├── service/                # AI Service Health 응답 검증
└── dto/                    # AI Service Health 공개 응답 DTO
_common/
├── ai_config/              # 두 AI Client가 공유하는 주소·timeout·RestClient 설정
├── config/                 # CORS, JSON, 범용 RestClient 생성 지원
├── exception/              # 공통 AI 호출 예외와 ProblemDetail 예외 처리
└── http/                   # AI 호출 실행 지원과 외부 Client timeout 판별
```

Kotlin 기본 패키지는 `ai.govbiz.core`이고 Gradle 프로젝트명은 `govbiz-core-api`입니다.

지원사업 검색 관련 코드는 `supportprogram` 기능 디렉터리에서 함께 관리합니다. 기능 안의 `controller → service → domain` 흐름과 `client/ai`, `client/bizinfo` 외부 연결을 한곳에서 따라갈 수 있습니다. DTO는 한 레이어에 모으되 브라우저 공개 계약은 `supportprogram/dto/api`, AI Service 내부 계약은 `supportprogram/dto/ai`, 기업마당 응답 계약은 `supportprogram/dto/bizinfo`로 구분합니다. BizInfo Client는 인증키와 공공데이터포털 전송 계약을 소유하고, Service가 HTML 제거·공식 원문 URL 검증·신청기간과 접수상태 계산을 담당합니다. AI 점수화 Service는 후보를 FastAPI에 보내고 ID·점수 합계·내림차순을 재검증합니다. Kotlin 단어 사전과 고정 관련도 가중치는 사용하지 않습니다.

계층 연결 예제인 SampleItem도 `_sampleitem/controller → service → domain`으로 독립되어 있으며 공개 요청·응답 형식은 `_sampleitem/dto`가 소유합니다.

Core API 프로세스 자체의 생존 상태는 `_health/controller`와 `_health/dto`가 담당합니다. AI Service 연결 상태를 확인하는 `_health_ai_service` 기능과는 별개입니다. `_health_ai_service/client/AiServiceHealthClient`는 내부 Health API만 호출하고, 지원사업 점수화 호출은 `supportprogram/client/ai/HttpAiSupportProgramRankingClient`가 담당합니다.

둘 이상의 기능이 실제로 함께 사용하는 코드만 `_common`에 둡니다. 앞의 밑줄은 IDE의 알파벳 정렬에서 공통 코드를 기능보다 위에 표시하려는 프로젝트 규칙입니다. `_common/ai_config`는 AI Service 주소·timeout과 공용 `RestClient` 설정만 담당합니다. `_common/http`는 연결 실패·timeout·응답 해석 실패의 공통 변환을, `_common/exception`은 공통 AI 호출 예외와 공개 ProblemDetail 변환을 담당합니다. 반면 HTTP 204·503·504가 각 기능에서 무엇을 뜻하는지는 Health와 지원사업 Client가 각각 판단합니다. `_common/config`는 전체 API의 JSON·CORS 정책과 범용 `RestClient` 생성 지원을 담당하고, `TimeoutCause`는 AI·기업마당 HTTP Client가 timeout 원인을 같은 방식으로 판별합니다.

## AI HTTP 호출의 공통 처리와 기능별 처리

AI Health와 지원사업 점수화는 같은 `RestClient` 설정과 통신 오류 형식을 사용하지만 서로 다른 내부
API입니다. 그래서 모든 코드를 하나로 합치지 않고, 실제로 의미가 같은 부분만 공통화합니다.

```text
AiServiceHealthClient ───────┐
                             ├─ executeAiServiceCall { 각 Client의 HTTP 호출 }
HttpAiSupportProgramRankingClient ─┘
                                  ├─ 연결 실패 공통 변환
                                  ├─ 네트워크 timeout 공통 변환
                                  └─ JSON 응답 해석 실패 공통 변환
```

공통 함수는
[`AiServiceCallSupport.kt`](src/main/kotlin/ai/govbiz/core/_common/http/AiServiceCallSupport.kt)에
있습니다. 각 Client가 전달한 코드 블록을 `try` 안에서 실행하고 Spring HTTP 예외를 공통
[`AiServiceCallException`](src/main/kotlin/ai/govbiz/core/_common/exception/AiServiceCallException.kt)으로
변환합니다.

```kotlin
fun <T> executeAiServiceCall(block: () -> T): T =
    try {
        block()
    } catch (exception: ResourceAccessException) {
        // 연결 실패 또는 네트워크 timeout
    } catch (exception: RestClientResponseException) {
        // Client가 별도로 처리하지 못한 HTTP 응답 오류
    } catch (exception: RestClientException) {
        // JSON 변환 실패 같은 RestClient 오류
    }
```

Health Client는 다음처럼 이 공통 함수 안에서 자기 HTTP 요청을 실행합니다.

```kotlin
fun getHealth(): AiServiceHealthPayload =
    executeAiServiceCall {
        restClient.get()
            .uri("/internal/v1/health")
            .retrieve()
            .onStatus(/* Health 응답 상태 해석 */)
            .toEntity(AiServiceHealthPayload::class.java)
    }
```

여기서 `executeAiServiceCall`은 연결·timeout·JSON 해석 오류를 처리하고, `onStatus`는 실제 HTTP
응답을 받은 뒤 그 상태 코드의 의미를 판단합니다. 숫자 대신 Spring 상수를 사용합니다.

| Spring 표현 | 실제 HTTP 상태 |
|---|---:|
| `HttpStatus.OK.value()` | 200 |
| `HttpStatus.NO_CONTENT.value()` | 204 |
| `HttpStatus.REQUEST_TIMEOUT.value()` | 408 |
| `HttpStatus.SERVICE_UNAVAILABLE.value()` | 503 |
| `HttpStatus.GATEWAY_TIMEOUT.value()` | 504 |

현재 상태 해석은 다음과 같습니다.

| 내부 API | 204 | 503 | 408·504 | 그 밖의 200이 아닌 상태 |
|---|---|---|---|---|
| Health | `INVALID_RESPONSE` | `UNAVAILABLE` | `UPSTREAM_ERROR` | `UPSTREAM_ERROR` |
| 지원사업 점수화 | `INVALID_RESPONSE` | `UNAVAILABLE` | `TIMEOUT` | `UPSTREAM_ERROR` |

204와 503은 현재 두 Client에서 같은 오류로 바뀌지만, 각 내부 API의 상태 계약을 코드에서 바로
확인할 수 있도록 `onStatus` 안에 명시적으로 둡니다. 공통화된 것은 그 뒤에 사용하는
`AiServiceCallException`의 네 가지 분류와 공개 ProblemDetail 변환입니다. 따라서 흐름은 다음과
같습니다.

```text
HTTP 상태를 기능별 Client가 해석
  → UPSTREAM_ERROR | INVALID_RESPONSE | UNAVAILABLE | TIMEOUT
  → ApiExceptionHandler가 공개 502 | 502 | 503 | 504 ProblemDetail로 변환
```

HTTP 200을 받았더라도 body가 없으면 각 Client가 `INVALID_RESPONSE`를 발생시킵니다. 연결조차 되지
않거나 소켓 timeout이 발생하면 HTTP 상태가 없으므로 `onStatus`가 아니라 공통
`executeAiServiceCall`이 처리합니다.

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
