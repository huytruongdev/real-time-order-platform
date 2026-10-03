package com.realtimeorder.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.support.TestAuth;
import com.realtimeorder.user.application.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2: tạo user ADMIN/RESTAURANT/DRIVER (ADR-035).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminUserIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserAdminService userAdminService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    TestAuth auth;

    @BeforeEach
    void setUp() {
        auth = new TestAuth(mockMvc, objectMapper, userAdminService);
    }

    @Test
    void ensureAdminIsIdempotent() {
        String email = TestAuth.uniqueEmail("admin");

        assertThat(userAdminService.ensureAdmin(email, TestAuth.PASSWORD, "Admin")).isTrue();
        assertThat(userAdminService.ensureAdmin(email.toUpperCase(), TestAuth.PASSWORD, "Admin")).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where email = ? and role = 'ADMIN'", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void ensureAdminDoesNotPromoteExistingUser() throws Exception {
        String email = TestAuth.uniqueEmail("customer");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", TestAuth.PASSWORD, "name", "C"))))
                .andExpect(status().isCreated());

        assertThat(userAdminService.ensureAdmin(email, TestAuth.PASSWORD, "Admin")).isFalse();

        assertThat(jdbcTemplate.queryForObject("select role from users where email = ?", String.class, email))
                .isEqualTo("CUSTOMER");
    }

    @Test
    void adminCreatesRestaurantUserWhoCanLogin() throws Exception {
        String email = TestAuth.uniqueEmail("restaurant");

        createUser(auth.adminBearer(), email, "RESTAURANT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("RESTAURANT"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESTAURANT"));
    }

    @Test
    void adminCannotCreateAdminOrCustomerThroughApi() throws Exception {
        String admin = auth.adminBearer();

        createUser(admin, TestAuth.uniqueEmail("x"), "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        createUser(admin, TestAuth.uniqueEmail("x"), "CUSTOMER")
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUserWithExistingEmailReturnsConflict() throws Exception {
        String admin = auth.adminBearer();
        String email = TestAuth.uniqueEmail("driver");
        createUser(admin, email, "DRIVER").andExpect(status().isCreated());

        createUser(admin, email, "DRIVER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void onlyAdminCanCreateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(TestAuth.uniqueEmail("x"), "DRIVER"))))
                .andExpect(status().isUnauthorized());

        createUser(auth.customerBearer(), TestAuth.uniqueEmail("x"), "DRIVER")
                .andExpect(status().isForbidden());
    }

    private ResultActions createUser(String bearer, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/users")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(userBody(email, role))));
    }

    private static Map<String, String> userBody(String email, String role) {
        return Map.of("email", email, "password", TestAuth.PASSWORD, "name", "Someone", "role", role);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
