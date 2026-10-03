package com.realtimeorder.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeorder.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1: toàn bộ auth flow qua HTTP với PostgreSQL thật.
 * Mỗi test dùng email riêng nên không cần dọn dữ liệu giữa các test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;

    // ---------- register ----------

    @Test
    void registerCreatesCustomerWithNormalizedEmail() throws Exception {
        String email = uniqueEmail();

        register(email.toUpperCase(), PASSWORD, "Alice")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerIgnoresRoleFromRequest() throws Exception {
        String body = json(Map.of("email", uniqueEmail(), "password", PASSWORD, "name", "Mallory", "role", "ADMIN"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void registerWithExistingEmailInDifferentCaseReturnsConflict() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD, "Alice 2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void registerWithInvalidInputReturnsValidationErrors() throws Exception {
        register("not-an-email", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
    }

    // ---------- login & access token ----------

    @Test
    void loginReturnsTokensAndAccessTokenAuthenticatesRequests() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());

        JsonNode tokens = login(email, PASSWORD);

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailReturnsSameError() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());

        loginRequest(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        loginRequest(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRequiresValidAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- refresh token ----------

    @Test
    void refreshRotatesRefreshToken() throws Exception {
        JsonNode first = registerAndLogin();

        JsonNode second = readJson(refresh(refreshToken(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(refreshToken(second)).isNotEqualTo(refreshToken(first));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(second)))
                .andExpect(status().isOk());
    }

    @Test
    void reusingRotatedRefreshTokenRevokesWholeFamily() throws Exception {
        JsonNode first = registerAndLogin();
        JsonNode second = readJson(refresh(refreshToken(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        // Dùng lại token cũ (đã bị rotate) -> reuse detected
        refresh(refreshToken(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Token mới nhất của cùng family cũng bị revoke
        refresh(refreshToken(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginTwiceCreatesIndependentFamilies() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());
        JsonNode deviceA = login(email, PASSWORD);
        JsonNode deviceB = login(email, PASSWORD);

        // Reuse trên device A không ảnh hưởng device B
        refresh(refreshToken(deviceA)).andExpect(status().isOk());
        refresh(refreshToken(deviceA)).andExpect(status().isUnauthorized());

        refresh(refreshToken(deviceB)).andExpect(status().isOk());
    }

    @Test
    void refreshWithUnknownTokenIsRejected() throws Exception {
        refresh("unknown-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void databaseStoresOnlyHashOfRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());
        String rawRefreshToken = refreshToken(login(email, PASSWORD));

        List<String> storedHashes = jdbcTemplate.queryForList("""
                select t.token_hash
                  from refresh_tokens t
                  join users u on u.id = t.user_id
                 where u.email = ?
                """, String.class, email);

        assertThat(storedHashes).containsExactly(sha256Hex(rawRefreshToken));
        assertThat(storedHashes).doesNotContain(rawRefreshToken);
    }

    // ---------- logout ----------

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent() throws Exception {
        JsonNode tokens = registerAndLogin();

        logout(refreshToken(tokens)).andExpect(status().isNoContent());
        logout(refreshToken(tokens)).andExpect(status().isNoContent());

        refresh(refreshToken(tokens)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private JsonNode registerAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Alice").andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    private ResultActions register(String email, String password, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password, "name", name))));
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = loginRequest(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readJson(body);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private static String refreshToken(JsonNode tokens) {
        return tokens.get("refreshToken").asText();
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
