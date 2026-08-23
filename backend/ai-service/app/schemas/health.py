from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict


class ServiceName(StrEnum):
    """서비스 간 Health 계약에서 사용하는 AI Service 식별자."""

    GOVBIZ_AI_SERVICE = "govbiz-ai-service"


class HealthResponse(BaseModel):
    """GET /internal/v1/health 응답 계약."""

    model_config = ConfigDict(frozen=True)

    status: Literal["up"]
    service: Literal[ServiceName.GOVBIZ_AI_SERVICE]
