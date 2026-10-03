package com.realtimeorder.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeorder.user.application.UserAdminService;
import com.realtimeorder.user.application.UserProfile;
import com.realtimeorder.user.domain.Role;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tạo user theo role và lấy access token qua HTTP login thật, dùng chung cho integration test.
 * Mỗi lần gọi tạo email riêng nên các test không ảnh hưởng nhau.
 */
public final class TestAuth {

    public static final String PASSWORD = "password123";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserAdminService userAdminService;

    public TestAuth(MockMvc mockMvc, ObjectMapper objectMapper, UserAdminService userAdminService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userAdminService = userAdminService;
    }

    public String adminBearer() throws Exception {
        String email = uniqueEmail("admin");
        userAdminService.ensureAdmin(email, PASSWORD, "Admin");
        return bearer(email);
    }

    public UserProfile createUser(Role role) {
        return userAdminService.createUser(uniqueEmail(role.name().toLowerCase()), PASSWORD, "Test " + role, role);
    }

    public String customerBearer() throws Exception {
        String email = uniqueEmail("customer");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", PASSWORD, "name", "Customer"))))
                .andExpect(status().isCreated());
        return bearer(email);
    }

    public String bearer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode tokens = objectMapper.readTree(body);
        return "Bearer " + tokens.get("accessToken").asText();
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
