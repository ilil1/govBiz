# GovBiz Docker Compose

Docker Compose는 React, Core API, AI Service를 한 번에 실행하고 실제 서비스 경계를 검증합니다.

```text
Browser (127.0.0.1:5173)
  → Vite web container
      → /api proxy
          → core-api:8080
              ├→ https://apis.data.go.kr
              └→ ai-service:8000
```

## 주소 규칙

| 호출 주체 | 사용하는 주소 | 이유 |
|---|---|---|
| 브라우저의 React | `/api/...` | Vite 프록시가 같은 Origin 요청을 Core API로 중계 |
| web 컨테이너 | `http://core-api:8080` | Compose 내부 DNS |
| Core API 컨테이너 | `http://ai-service:8000` | Compose 내부 DNS |
| Core API 컨테이너 | `https://apis.data.go.kr` | 실제 기업마당 공고 upstream |
| Host 터미널 | `http://127.0.0.1:8080` | Host에 공개된 Core API 포트 |

`core-api`와 `ai-service`는 컨테이너 네트워크 안에서만 해석되는 이름입니다. 브라우저 JavaScript가
`http://core-api:8080`을 직접 호출하면 실패합니다.

## 실행

저장소 루트의 `.env`에 공공데이터포털에서 발급한 일반 인증키를 넣고 실행합니다. Encoding 또는
Decoding 키를 사용할 수 있으며 Core API가 호출 전에 정규화합니다. `.env`는 Git에서 제외되며
키는 `core-api` 컨테이너에만 전달됩니다.

```dotenv
DATA_GO_KR_SERVICE_KEY=발급받은_인증키
```

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `BIZINFO_API_BASE_URL` | `https://apis.data.go.kr` | 공고 API origin. 로컬 스텁 검증 외에는 변경하지 않음 |
| `BIZINFO_API_CONNECT_TIMEOUT` | `2s` | 외부 API 연결 제한시간 |
| `BIZINFO_API_READ_TIMEOUT` | `10s` | 외부 API 응답 제한시간 |

저장소 루트에서 실행합니다.

```bash
docker compose --env-file .env --file infrastructure/compose.yaml up --build
```

| 주소 | 용도 |
|---|---|
| `http://127.0.0.1:5173` | React Vite 개발 서버 |
| `http://127.0.0.1:5173/api/v1/health` | Vite 프록시를 거친 Core API Health |
| `http://127.0.0.1:5173/api/v1/support-programs/search?query=%EC%88%98%EC%B6%9C&acceptingOnly=true` | Vite 프록시를 거친 실제 공고 검색 |
| `http://127.0.0.1:5173/api/v1/sample-items/prepare` | Vite 프록시를 거친 SampleItem 준비 API |
| `http://127.0.0.1:5173/api/v1/health/ai-service` | Core API를 거친 AI Service Health |

중지와 정리:

```bash
docker compose --file infrastructure/compose.yaml down --volumes --remove-orphans
```

## 통합 smoke

다음 스크립트는 별도 Compose 프로젝트를 사용해 이미지를 빌드하고 다음을 확인합니다.

```bash
./infrastructure/scripts/verify-compose.sh
```

검증 스크립트는 `verification` profile의 `bizinfo-stub`을 사용하고, `BIZINFO_API_BASE_URL`과
`DATA_GO_KR_SERVICE_KEY`를 각각 스텁 주소와 percent-encoded dummy key로 강제합니다. 스텁은 디코딩된
키를 확인하므로 Encoding 키가 외부 요청에서 정확히 한 번만 인코딩되는 경로도 검증합니다. 따라서
루트 `.env`의 개인 키를 사용하거나 외부로 보내지 않고, 공공데이터포털의 네트워크 상태나 응답 변경에도
영향받지 않습니다. 일반 `docker compose up`에서는 이 profile이 비활성화되어 실제 API를 사용합니다.

1. Vite Web 응답이 200인지 확인합니다.
2. Vite 프록시를 거친 Core API Health가 200인지 확인합니다.
3. 검색 GET이 Web → Core API → Bizinfo adapter → 로컬 스텁을 거쳐 고정 공고를 반환하는지 확인합니다.
4. SampleItem 준비 POST가 200과 `READY_FOR_PROCESSING`을 반환하는지 확인합니다.
5. Core API를 통한 AI Service Health가 200인지 확인합니다.
6. AI Service를 중지했을 때 Core Health는 200, AI Health는 503인지 확인합니다.
7. AI Service 재시작 후 Core API 재시작 없이 Health가 복구되는지 확인합니다.

기본적으로 5173과 8080을 사용하므로, 같은 포트를 쓰는 다른 Compose 프로젝트는 중지한 뒤 실행하세요.
