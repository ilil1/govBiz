from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.search_intents import router as search_intents_router
from app.agents.search_intent.port import SearchIntentAnalyzer
from app.bootstrap import build_application_container
from app.config import Settings


def create_app(
    *,
    settings: Settings | None = None,
    search_intent_agent: SearchIntentAnalyzer | None = None,
) -> FastAPI:
    """FastAPI 객체를 조립하는 애플리케이션 팩토리다."""
    container = build_application_container(
        settings or Settings.from_environment(),
        search_intent_agent=search_intent_agent,
    )

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncGenerator[None]:
        try:
            yield
        finally:
            await container.close()

    application = FastAPI(
        title="GovBiz AI Service",
        description="GovBiz Core API가 내부에서 호출하는 AI 분석 서비스",
        version="0.1.0",
        lifespan=lifespan,
    )
    application.state.container = container
    application.include_router(health_router)
    application.include_router(search_intents_router)
    return application


app = create_app()
