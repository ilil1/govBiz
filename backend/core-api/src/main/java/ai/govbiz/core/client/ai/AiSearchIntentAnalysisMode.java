package ai.govbiz.core.client.ai;

/** AI Service가 의도를 분석한 경로다. 공개 API에는 노출하지 않는다. */
public enum AiSearchIntentAnalysisMode {
    LLM,
    RULE_BASED_FALLBACK
}
