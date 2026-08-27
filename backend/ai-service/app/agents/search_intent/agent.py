import asyncio

from agents import (
    Agent,
    MaxTurnsExceeded,
    Model,
    ModelBehaviorError,
    ModelRefusalError,
    ModelSettings,
    ModelTimeoutError,
    RunConfig,
    Runner,
)
from openai import OpenAIError
from openai.types.shared import Reasoning
from pydantic import ValidationError

from app.agents.errors import AgentExecutionError

from .models import ExtractedSearchIntent
from .prompt import SEARCH_INTENT_INSTRUCTIONS


class SearchIntentAgent:
    """단일 turn에서 검색 의도를 구조화하는 typed agent."""

    def __init__(
        self,
        *,
        model: Model,
        model_timeout_seconds: float,
        run_timeout_seconds: float,
    ) -> None:
        self._run_timeout_seconds = run_timeout_seconds
        self._agent: Agent[None] = Agent(
            name="GovBiz Search Intent Extractor",
            instructions=SEARCH_INTENT_INSTRUCTIONS,
            model=model,
            output_type=ExtractedSearchIntent,
            model_settings=ModelSettings(
                max_tokens=500,
                reasoning=Reasoning(effort="none"),
                store=False,
                timeout=model_timeout_seconds,
            ),
        )
        self._run_config = RunConfig(
            workflow_name="GovBiz search intent analysis",
            tracing_disabled=True,
            trace_include_sensitive_data=False,
        )

    async def analyze(self, query: str) -> ExtractedSearchIntent:
        """실제 Runner를 한 turn만 실행하고 검증된 최종 출력만 반환한다."""

        try:
            async with asyncio.timeout(self._run_timeout_seconds):
                result = await Runner.run(
                    self._agent,
                    query,
                    max_turns=1,
                    run_config=self._run_config,
                )
        except (
            MaxTurnsExceeded,
            ModelBehaviorError,
            ModelRefusalError,
            ModelTimeoutError,
            OpenAIError,
            TimeoutError,
            ValidationError,
        ) as error:
            raise AgentExecutionError(
                "Search intent agent did not produce a usable result"
            ) from error

        output = result.final_output
        if not isinstance(output, ExtractedSearchIntent):
            raise AgentExecutionError(
                "Search intent agent returned an unexpected output type"
            )
        return output
