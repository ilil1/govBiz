package io.basearchitecture.core.controller;

import io.basearchitecture.core.config.JsonDeserializationConfig;
import io.basearchitecture.core.config.WebCorsConfig;
import io.basearchitecture.core.service.SampleItemPreparationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SampleItemPreparationController.class)
@Import({
        SampleItemPreparationService.class,
        ApiExceptionHandler.class,
        JsonDeserializationConfig.class,
        WebCorsConfig.class
})
class SampleItemPreparationControllerTest {

    private static final String PATH = "/api/v1/sample-items/prepare";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preparesAValidSampleItemWithoutStartingProcessing() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "item": {
                                    "name": "  Example item  ",
                                    "category": "BASIC",
                                    "note": "  Demonstrates the vertical slice.  "
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.phase").value("READY_FOR_PROCESSING"))
                .andExpect(jsonPath("$.item.name").value("Example item"))
                .andExpect(jsonPath("$.item.category").value("BASIC"))
                .andExpect(jsonPath("$.item.note").value("Demonstrates the vertical slice."))
                .andExpect(jsonPath("$.processing.status").value("NOT_STARTED"));
    }

    @Test
    void preservesUnknownOptionalValuesAsNull() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "item": { "name": "Example item", "category": null, "note": null } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.category").value(nullValue()))
                .andExpect(jsonPath("$.item.note").value(nullValue()));
    }

    @Test
    void rejectsBlankRequiredValuesWithStableProblemDetail() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"item\": { \"name\": \" \" } }"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:base-architecture:problem:request-validation-failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("item.name"));
    }

    @Test
    void rejectsAQuotedEnumBeforeItReachesTheDomainLayer() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"item\": { \"name\": \"Example\", \"category\": 1 } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
    }
}
