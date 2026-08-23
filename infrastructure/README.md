# GovBiz Docker Compose

Docker Compose는 React, Core API, AI Service를 한 번에 실행하고 실제 서비스 경계를 검증합니다.

```text
Browser (127.0.0.1:5173)
  → Vite web container
      → /api proxy
          → core-api:8080
              → ai-service:8000
```

## 주소 규칙

| 호출 주체 | 사용하는 주소 | 이유 |
|---|---|---|
| 브라우저의 React | `/api/...` | Vite 프록시가 같은 Origin 요청을 Core API로 중계 |
| web 컨테이너 | `http://core-api:8080` | Compose 내부 DNS |
| Core API 컨테이너 | `http://ai-service:8000` | Compose 내부 DNS |
| Host 터미널 | `http://127.0.0.1:8080` | Host에 공개된 Core API 포트 |

`core-api`와 `ai-service`는 컨테이너 네트워크 안에서만 해석되는 이름입니다. 브라우저 JavaScript가
`http://core-api:8080`을 직접 호출하면 실패합니다.

## 실행

저장소 루트에서 실행합니다.

```bash
docker compose --file infrastructure/compose.yaml up --build
```

| 주소 | 용도 |
|---|---|
| `http://127.0.0.1:5173` | React Vite 개발 서버 |
| `http://127.0.0.1:5173/api/v1/health` | Vite 프록시를 거친 Core API Health |
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

1. Vite Web 응답이 200인지 확인합니다.
2. Vite 프록시를 거친 Core API Health가 200인지 확인합니다.
3. SampleItem 준비 POST가 200과 `READY_FOR_PROCESSING`을 반환하는지 확인합니다.
4. Core API를 통한 AI Service Health가 200인지 확인합니다.
5. AI Service를 중지했을 때 Core Health는 200, AI Health는 503인지 확인합니다.
6. AI Service 재시작 후 Core API 재시작 없이 Health가 복구되는지 확인합니다.

기본적으로 5173과 8080을 사용하므로, 같은 포트를 쓰는 다른 Compose 프로젝트는 중지한 뒤 실행하세요.
