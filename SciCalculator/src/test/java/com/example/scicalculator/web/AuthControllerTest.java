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
 * End-to-end auth tests through the real security filter chain (MockMvc + full context, default
 * profile → {@code appFilterChain}).
 *
 * <p>Proves the whole loop: register hashes+stores a user, login verifies and returns a JWT, and a
 * protected endpoint rejects a tokenless request (401) but serves one carrying a valid Bearer token
 * (200), running as that user.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The whole suite shares one named in-memory DB (DB_CLOSE_DELAY=-1), and username is UNIQUE, so
     * usernames must be unique across test CLASSES too. This class uses an "authuser-" prefix that's
     * disjoint from CalculationServiceTest's "user-" seeds.
     */
    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    private String uniqueUsername() {
        return "authuser-" + USER_SEQ.incrementAndGet();
    }

    private String json(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registeringDuplicateUsernameConflicts() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithBlankUsernameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidTokenRunsAsThatUser() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "secret")))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginBody =
                objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String token = (String) loginBody.get("token");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }
}
