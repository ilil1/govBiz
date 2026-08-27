# 지원사업 검색 HTTP 계약

GovBiz Web은 공공데이터포털 인증키를 보유하거나 외부 API를 직접 호출하지 않습니다. 브라우저는
Core API의 검색 endpoint만 호출하고, Core API가 공공데이터포털의 기업마당 공고를 GovBiz 도메인
모델로 변환합니다. 자연어 의도 분석은 Core API 뒤의 내부 구현이며 공개 계약을 변경하지 않습니다.

```text
Browser
  → GET /api/v1/support-programs/search
      → Core API
          ├→ POST /internal/v1/search-intents/analyze → AI Service
          └→ 중소벤처기업부 중소기업 지원사업 공고 조회 서비스
```

## 요청

```http
GET /api/v1/support-programs/search?query=%EC%88%98%EC%B6%9C&acceptingOnly=true
Accept: application/json
```

| Query parameter | 필수 | 설명 |
|---|---|---|
| `query` | 예 | 사용자가 입력한 검색 문장 또는 키워드 |
| `acceptingOnly` | 아니요 | `true`이면 현재 접수 중인 공고만 반환. 기본값은 `true` |

응답은 관련도와 공고 갱신시각을 기준으로 정렬한 최대 5개 공고를 포함합니다.

## 내부 검색 의도 계약

Core API만 다음 FastAPI endpoint를 호출합니다. 브라우저에서 직접 호출하지 않습니다.

```http
POST /internal/v1/search-intents/analyze
Content-Type: application/json

{
  "query": "서울 AI 창업지원 찾아줘",
  "acceptingOnly": true
}
```

```json
{
  "originalQuery": "서울 AI 창업지원 찾아줘",
  "keywords": [],
  "regions": ["서울"],
  "categories": ["AI", "창업"],
  "targetTerms": [],
  "acceptingOnly": true,
  "clarificationNeeded": false,
  "clarificationQuestion": null
}
```

`OPENAI_API_KEY`는 필수이며, timeout·인증·rate limit·refusal·잘못된 structured output이 발생하면
AI Service는 불완전한 규칙 결과 대신 오류를 반환합니다.

Core API는 원문 토큰을 보존하면서 `originalQuery`와 `acceptingOnly` echo, 배열 길이·문자열 길이,
허용 지역·분야 값과 clarification 필드 조합을 다시 검증한 AI 분석만 병합합니다. AI Service의 HTTP
오류, timeout, JSON 오류, echo 불일치나 유효하지 않은 값은 공개 502·503·504로 변환하며 로컬 parser
성공으로 숨기지 않습니다. 내부 검색 의도 DTO는 공개 성공 응답에 추가되지 않습니다.

## 성공 응답

```json
{
  "query": "수출",
  "programs": [
    {
      "id": "공고 식별자",
      "title": "공고명",
      "organization": "수행기관명",
      "summary": "사업 개요",
      "categories": ["수출"],
      "regions": ["전국"],
      "targetDescription": "지원 대상",
      "supportAmount": "정보 없음",
      "applicationPeriod": "예산 소진시까지",
      "applicationStartDate": null,
      "applicationEndDate": null,
      "status": "OPEN",
      "sourceName": "기업마당",
      "sourceUrl": "https://www.bizinfo.go.kr/example",
      "matchedReasons": ["수출 분야", "현재 접수 중"]
    }
  ]
}
```

- `applicationPeriod`는 `예산 소진시까지`, `모집 완료시`, `세부사업별 상이` 같은 원문의 신청기간을
  보존합니다.
- `applicationStartDate`와 `applicationEndDate`는 날짜를 확실히 해석할 수 있을 때만 ISO 8601
  `YYYY-MM-DD`로 제공하고, 그렇지 않으면 `null`입니다.
- `status`는 `OPEN`, `UPCOMING`, `CLOSED`, `UNKNOWN` 중 하나입니다. 날짜 근거가 불충분한 공고를
  접수 중으로 추정하지 않습니다. 다만 `예산 소진시까지`, `상시`, `모집 완료시`처럼 원문이
  명시적으로 계속 접수한다고 밝힌 경우는 `OPEN`으로 분류합니다.
- `supportAmount`처럼 원본에 독립된 값이 없는 필드는 추정하지 않고 `정보 없음`으로 제공합니다.
- `sourceUrl`은 사용자가 사실을 확인할 수 있는 공식 원문 주소입니다.

## 비밀정보와 오류 처리

`DATA_GO_KR_SERVICE_KEY`는 Core API 프로세스에, `OPENAI_API_KEY`는 AI Service 프로세스에만
주입합니다. 응답, 로그, Frontend 환경변수와 Git 파일에 인증키를 포함하지 않습니다. 기업마당 또는
AI Service의 설정 누락·장애·시간 초과·잘못된 응답은 Core API가 `application/problem+json` 오류로
변환하며, 외부 URL이나 내부 예외 메시지를 브라우저에 노출하지 않습니다.

| 상황 | HTTP | `code` |
|---|---:|---|
| 인증키 미설정 | 503 | `SUPPORT_PROGRAM_SOURCE_NOT_CONFIGURED` |
| 외부 API 실패 응답 | 502 | `SUPPORT_PROGRAM_SOURCE_ERROR` |
| 잘못된 외부 응답 | 502 | `SUPPORT_PROGRAM_INVALID_RESPONSE` |
| 외부 API 연결 불가 | 503 | `SUPPORT_PROGRAM_SOURCE_UNAVAILABLE` |
| 외부 API 시간 초과 | 504 | `SUPPORT_PROGRAM_SOURCE_TIMEOUT` |
| AI Service 실패 응답 | 502 | `AI_SERVICE_UPSTREAM_ERROR` |
| AI Service 잘못된 응답 | 502 | `AI_SERVICE_INVALID_RESPONSE` |
| AI Service 연결 불가·OpenAI 분석 불가 | 503 | `AI_SERVICE_UNAVAILABLE` |
| AI Service 시간 초과 | 504 | `AI_SERVICE_TIMEOUT` |

단위·계약 테스트는 가짜 Agent와 HTTP mock을 사용하며 실제 OpenAI 네트워크를 호출하지 않습니다.
Compose smoke는 더미 OpenAI 키로 AI Service 시작·중지·복구와 필수 AI 장애 전파를 확인하고, 빈
검색어로 공공데이터 스텁 adapter 경로를 별도로 검증합니다. 더미 키를 실제 OpenAI로 보내지는 않습니다.
