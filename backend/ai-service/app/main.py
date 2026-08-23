from fastapi import FastAPI

from app.api.health import router as health_router


def create_app() -> FastAPI:
    """FastAPI 객체를 조립하는 애플리케이션 팩토리다."""
    application = FastAPI(
        title="Base Architecture Starter Service",
        description="Base Architecture Core API가 내부에서 호출하는 AI 분석 서비스",
        version="0.1.0",
    )
    application.include_router(health_router)
    return application


app = create_app()
