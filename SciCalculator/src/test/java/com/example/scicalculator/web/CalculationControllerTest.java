package com.example.scicalculator.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end calculation API tests through the real security chain (MockMvc + full context, default
 * profile → {@code appFilterChain}) with real JWTs — no {@code @WithMockUser}. Each test registers and
 * logs in to obtain a Bearer token, then drives the calculation endpoints as that user.
 *
 * <p>Also exercises {@link ApiExceptionHandler}: divide-by-zero → 400, unknown/not-yours id → 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalculationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The whole suite shares one named in-memory DB (DB_CLOSE_DELAY=-1) and username is UNIQUE, so
     * usernames must be unique across test CLASSES too. This class uses a "calcapi-" prefix that's
     * disjoint from the other suites' seeds.
     */
    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    private String uniqueUsername() {
        return "calcapi-" + USER_SEQ.incrementAndGet();
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Registers (201) + logs in (200) the given user and returns the {@code "Bearer <token>"} header value. */
    private String registerAndLogin(String username) throws Exception {
        String credentials = json(Map.of("username", username, "password", "secret"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginBody =
                objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String token = (String) loginBody.get("token");
        assertThat(token).isNotBlank();
        return "Bearer " + token;
    }

    /** Creates a calculation as the holder of {@code authHeader} and returns its generated id. */
    private long createCalculation(String authHeader, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/calculations")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> body =
                objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return ((Number) body.get("id")).longValue();
    }

    private String stepJson(String operation, String operand) throws Exception {
        return json(Map.of("operation", operation, "operand", operand));
    }

    @Test
    void createReturnsCreatedCalculationOwnedByCaller() throws Exception {
        String username = uniqueUsername();
        String auth = registerAndLogin(username);

        mockMvc.perform(post("/api/calculations")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "demo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.owner").value(username))
                .andExpect(jsonPath("$.currentValue").value(0));
    }

    @Test
    void appendStepReturnsCreatedStep() throws Exception {
        String auth = registerAndLogin(uniqueUsername());
        long id = createCalculation(auth, "demo");

        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepJson("ADD", "5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenceNumber").value(1))
                .andExpect(jsonPath("$.resultAfter").value(5));
    }

    @Test
    void getReturnsCalculationWithStepsInOrder() throws Exception {
        String auth = registerAndLogin(uniqueUsername());
        long id = createCalculation(auth, "demo");

        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepJson("ADD", "5")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepJson("MULTIPLY", "2")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/calculations/" + id)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.currentValue").value(10))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.steps[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$.steps[1].resultAfter").value(10));
    }

    @Test
    void divideByZeroStepIsBadRequest() throws Exception {
        String auth = registerAndLogin(uniqueUsername());
        long id = createCalculation(auth, "demo");

        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepJson("DIVIDE", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCalculationIsNotFound() throws Exception {
        String auth = registerAndLogin(uniqueUsername());

        mockMvc.perform(get("/api/calculations/999999")
                        .header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossOwnerAccessIsNotFound() throws Exception {
        String authA = registerAndLogin(uniqueUsername());
        long id = createCalculation(authA, "demo");

        // A different authenticated user must not see A's calculation: 404, not 403.
        String authB = registerAndLogin(uniqueUsername());
        mockMvc.perform(get("/api/calculations/" + id)
                        .header("Authorization", authB))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "demo"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void appendStepWithMissingFieldsIsBadRequest() throws Exception {
        String auth = registerAndLogin(uniqueUsername());
        long id = createCalculation(auth, "demo");

        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyStampsTheAuthenticatedUserOnEveryRevision() throws Exception {
        // THE PAYOFF: a real authenticated write goes through the whole chain — JwtAuthenticationFilter
        // sets the SecurityContext, AuditUserFilter copies the username into AuditUserContext, and the
        // Envers listener stamps it onto each revision. So history shows the caller, NOT "system".
        String username = uniqueUsername();
        String auth = registerAndLogin(username);
        long id = createCalculation(auth, "demo");

        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepJson("ADD", "5")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/calculations/" + id + "/steps")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stepJson("MULTIPLY", "2")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/calculations/" + id + "/history")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[0].operation").value("ADD"))
                .andExpect(jsonPath("$[0].resultAfter").value(5))
                .andExpect(jsonPath("$[0].username").value(username))
                .andExpect(jsonPath("$[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$[1].operation").value("MULTIPLY"))
                .andExpect(jsonPath("$[1].resultAfter").value(10))
                .andExpect(jsonPath("$[1].username").value(username));
    }

    @Test
    void crossOwnerHistoryIsNotFound() throws Exception {
        String authA = registerAndLogin(uniqueUsername());
        long id = createCalculation(authA, "demo");

        String authB = registerAndLogin(uniqueUsername());
        mockMvc.perform(get("/api/calculations/" + id + "/history")
                        .header("Authorization", authB))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/calculations/999999/history"))
                .andExpect(status().isUnauthorized());
    }
}
