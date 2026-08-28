package ai.govbiz.core.controller

import ai.govbiz.core.config.JsonDeserializationConfig
import ai.govbiz.core.config.WebCorsConfig
import ai.govbiz.core.service.SampleItemPreparationService
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SampleItemPreparationController::class)
@Import(
    SampleItemPreparationService::class,
    ApiExceptionHandler::class,
    JsonDeserializationConfig::class,
    WebCorsConfig::class,
)
class SampleItemPreparationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun preparesAValidSampleItemWithoutStartingProcessing() {
        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "item": {
                        "name": "  Example item  ",
                        "category": "BASIC",
                        "note": "  Demonstrates the vertical slice.  "
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.phase").value("READY_FOR_PROCESSING"))
            .andExpect(jsonPath("$.item.name").value("Example item"))
            .andExpect(jsonPath("$.item.category").value("BASIC"))
            .andExpect(jsonPath("$.item.note").value("Demonstrates the vertical slice."))
            .andExpect(jsonPath("$.processing.status").value("NOT_STARTED"))
    }

    @Test
    fun preservesUnknownOptionalValuesAsNull() {
        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "item": { "name": "Example item", "category": null, "note": null } }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.category").value(nullValue()))
            .andExpect(jsonPath("$.item.note").value(nullValue()))
    }

    @Test
    fun rejectsBlankRequiredValuesWithStableProblemDetail() {
        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"item\": { \"name\": \" \" } }"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:govbiz:problem:request-validation-failed"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("item.name"))
    }

    @Test
    fun rejectsAQuotedEnumBeforeItReachesTheDomainLayer() {
        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"item\": { \"name\": \"Example\", \"category\": 1 } }"),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
    }

    private companion object {
        const val PATH = "/api/v1/sample-items/prepare"
    }
}
