from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_returns_typed_health_response() -> None:
    response = client.get("/internal/v1/health")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    assert response.json() == {
        "status": "up",
        "service": "base-architecture-ai-service",
    }


def test_exposes_health_contract_in_openapi() -> None:
    response = client.get("/openapi.json")

    assert response.status_code == 200
    document = response.json()
    health_operation = document["paths"]["/internal/v1/health"]["get"]
    success_schema = health_operation["responses"]["200"]["content"][
        "application/json"
    ]["schema"]
    assert success_schema["$ref"] == "#/components/schemas/HealthResponse"

    health_schema = document["components"]["schemas"]["HealthResponse"]
    assert set(health_schema["required"]) == {"status", "service"}
    assert health_schema["properties"]["status"]["const"] == "up"
    assert (
        health_schema["properties"]["service"]["const"]
        == "base-architecture-ai-service"
    )
