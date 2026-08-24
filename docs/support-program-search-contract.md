# 지원사업 검색 HTTP 계약

GovBiz Web은 공공데이터포털 인증키를 보유하거나 외부 API를 직접 호출하지 않습니다. 브라우저는
Core API의 검색 endpoint만 호출하고, Core API가 공공데이터포털의 기업마당 공고를 GovBiz 도메인
모델로 변환합니다.

```text
Browser
  → GET /api/v1/support-programs/search
      → Core API
          → 중소벤처기업부 중소기업 지원사업 공고 조회 서비스
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

`DATA_GO_KR_SERVICE_KEY`는 Core API 프로세스에만 주입합니다. 응답, 로그, Frontend 환경변수와 Git
파일에 인증키를 포함하지 않습니다. 키 누락, 외부 API 장애, 시간 초과 또는 잘못된 외부 응답은
Core API가 `application/problem+json` 오류로 변환하며, 외부 URL이나 내부 예외 메시지를 브라우저에
노출하지 않습니다.

| 상황 | HTTP | `code` |
|---|---:|---|
| 인증키 미설정 | 503 | `SUPPORT_PROGRAM_SOURCE_NOT_CONFIGURED` |
| 외부 API 실패 응답 | 502 | `SUPPORT_PROGRAM_SOURCE_ERROR` |
| 잘못된 외부 응답 | 502 | `SUPPORT_PROGRAM_INVALID_RESPONSE` |
| 외부 API 연결 불가 | 503 | `SUPPORT_PROGRAM_SOURCE_UNAVAILABLE` |
| 외부 API 시간 초과 | 504 | `SUPPORT_PROGRAM_SOURCE_TIMEOUT` |

단위·계약 테스트와 Compose smoke는 실제 인증키나 외부 네트워크를 사용하지 않습니다. Compose
smoke는 검증 profile의 로컬 스텁을 사용해 Web → Core API → 외부 adapter 전체 경로를
재현합니다.
