from dataclasses import dataclass

from agents import OpenAIResponsesModel
from openai import AsyncOpenAI

from app.agents.search_intent.agent import SearchIntentAgent
from app.agents.search_intent.port import SearchIntentAnalyzer
from app.agents.search_intent.service import SearchIntentAnalysisService
from app.config import Settings


@dataclass(slots=True)
class ApplicationContainer:
    """애플리케이션 객체 그래프와 그 객체가 소유한 자원."""

    search_intent_service: SearchIntentAnalysisService
    openai_client: AsyncOpenAI | None = None

    async def close(self) -> None:
        if self.openai_client is not None:
            await self.openai_client.close()


def build_application_container(
    settings: Settings,
    *,
    search_intent_agent: SearchIntentAnalyzer | None = None,
) -> ApplicationContainer:
    """환경설정과 선택적 테스트 대역을 실제 애플리케이션 객체로 조립한다."""

    openai_client: AsyncOpenAI | None = None
    agent = search_intent_agent

    if agent is None:
        openai_client = AsyncOpenAI(
            api_key=settings.openai_api_key,
            timeout=settings.llm_model_timeout_seconds,
            max_retries=0,
        )
        model = OpenAIResponsesModel(
            model=settings.openai_model,
            openai_client=openai_client,
        )
        agent = SearchIntentAgent(
            model=model,
            model_timeout_seconds=settings.llm_model_timeout_seconds,
            run_timeout_seconds=settings.llm_run_timeout_seconds,
        )

    return ApplicationContainer(
        search_intent_service=SearchIntentAnalysisService(agent),
        openai_client=openai_client,
    )
