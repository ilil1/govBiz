from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.search_intents import router as search_intents_router
from app.api.health import router as health_router
from app.config import Settings
from app.providers.search_intent import SearchIntentProvider
from app.services.search_intent import (
    SearchIntentAnalysisService,
    build_search_intent_service,
)


def create_app(
    *,
    settings: Settings | None = None,
    search_intent_provider: SearchIntentProvider | None = None,
) -> FastAPI:
    """FastAPI 객체를 조립하는 애플리케이션 팩토리다."""
    if search_intent_provider is None:
        search_intent_service = build_search_intent_service(
            settings or Settings.from_environment()
        )
    else:
        search_intent_service = SearchIntentAnalysisService(search_intent_provider)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            await search_intent_service.close()

    application = FastAPI(
        title="GovBiz AI Service",
        description="GovBiz Core API가 내부에서 호출하는 AI 분석 서비스",
        version="0.1.0",
        lifespan=lifespan,
    )
    application.state.search_intent_service = search_intent_service
    application.include_router(health_router)
    application.include_router(search_intents_router)
    return application


app = create_app()
