from fastapi import APIRouter

from app.schemas.health import HealthResponse, ServiceName

router = APIRouter(prefix="/internal/v1", tags=["internal"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="AI Service 상태 확인",
)
def health() -> HealthResponse:
    """Core API가 AI Service의 실행 상태를 확인할 때 사용한다."""
    return HealthResponse(
        status="up",
        service=ServiceName.BASE_ARCHITECTURE_AI_SERVICE,
    )
