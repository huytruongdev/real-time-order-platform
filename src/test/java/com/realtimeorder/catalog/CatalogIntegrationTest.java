package com.realtimeorder.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.support.TestAuth;
import com.realtimeorder.user.application.UserAdminService;
import com.realtimeorder.user.domain.Role;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2: Catalog API qua HTTP với PostgreSQL thật.
 * Mỗi test tạo restaurant riêng nên không phụ thuộc dữ liệu của test khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserAdminService userAdminService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    TestAuth auth;
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        auth = new TestAuth(mockMvc, objectMapper, userAdminService);
        admin = auth.adminBearer();
    }

    // ---------- restaurant ----------

    @Test
    void adminCreatesRestaurantAndCustomerCanViewWithoutLogin() throws Exception {
        UUID owner = auth.createUser(Role.RESTAURANT).id();

        JsonNode created = readJson(createRestaurant(owner, "Phở Hà Nội")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(owner.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0)));

        mockMvc.perform(get("/api/v1/restaurants/{id}", created.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Phở Hà Nội"))
                // Dữ liệu nội bộ không lộ ra API public.
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void restaurantOwnerMustBeExistingRestaurantUser() throws Exception {
        createRestaurant(UUID.randomUUID(), "Ghost owner")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESTAURANT_OWNER"));

        createRestaurant(auth.createUser(Role.DRIVER).id(), "Driver owner")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESTAURANT_OWNER"));
    }

    @Test
    void publicListContainsOnlyActiveRestaurants() throws Exception {
        UUID active = newRestaurant();
        UUID inactive = newRestaurant();
        deactivateRestaurant(inactive);

        JsonNode page = readJson(mockMvc.perform(get("/api/v1/restaurants").param("size", "100"))
                .andExpect(status().isOk()));

        Integer activeCount = jdbcTemplate.queryForObject(
                "select count(*) from restaurants where status = 'ACTIVE'", Integer.class);
        assertThat(page.get("totalElements").asInt()).isEqualTo(activeCount);

        mockMvc.perform(get("/api/v1/restaurants/{id}", active)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/restaurants/{id}", inactive))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/restaurants/{id}/products", inactive))
                .andExpect(status().isNotFound());

        // Admin vẫn thấy restaurant INACTIVE.
        adminRequest(get("/api/v1/admin/restaurants/{id}", inactive))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void updateWithStaleVersionIsRejected() throws Exception {
        UUID restaurantId = newRestaurant();

        updateRestaurant(restaurantId, "Tên mới", "ACTIVE", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tên mới"))
                .andExpect(jsonPath("$.version").value(1));

        // Admin thứ hai vẫn giữ version 0 đã đọc trước đó.
        updateRestaurant(restaurantId, "Tên khác", "ACTIVE", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        adminRequest(get("/api/v1/admin/restaurants/{id}", restaurantId))
                .andExpect(jsonPath("$.name").value("Tên mới"));
    }

    // ---------- product ----------

    @Test
    void adminCreatesProductAndCustomerSeesOnlyActiveProductsSortedByName() throws Exception {
        UUID restaurantId = newRestaurant();
        createProduct(restaurantId, "Phở bò", "55000.00", 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(55000.00))
                .andExpect(jsonPath("$.availableStock").value(10))
                .andExpect(jsonPath("$.reservedStock").value(0));
        createProduct(restaurantId, "Bún chả", "45000", 5).andExpect(status().isCreated());
        UUID hidden = UUID.fromString(readJson(createProduct(restaurantId, "Cơm tấm", "40000", 5)).get("id").asText());
        updateProduct(hidden, "Cơm tấm", "40000", "INACTIVE", 0).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/restaurants/{id}/products", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Bún chả"))
                .andExpect(jsonPath("$.content[1].name").value("Phở bò"))
                .andExpect(jsonPath("$.content[0].reservedStock").doesNotExist())
                .andExpect(jsonPath("$.content[0].version").doesNotExist());

        adminRequest(get("/api/v1/admin/restaurants/{id}/products", restaurantId))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void productsArePaginated() throws Exception {
        UUID restaurantId = newRestaurant();
        for (int i = 1; i <= 5; i++) {
            createProduct(restaurantId, "Món " + i, "10000", 1).andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/restaurants/{id}/products", restaurantId)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Món 3"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void invalidPaginationReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("size")));
    }

    @Test
    void invalidProductInputReturnsValidationErrors() throws Exception {
        UUID restaurantId = newRestaurant();

        createProduct(restaurantId, "", "0", -1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("price")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("initialStock")));

        createProduct(restaurantId, "Lẻ tiền", "1000.123", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("price")));
    }

    @Test
    void createProductForUnknownRestaurantReturnsNotFound() throws Exception {
        createProduct(UUID.randomUUID(), "Phở", "50000", 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    }

    // ---------- stock ----------

    @Test
    void stockAdjustmentAddsAndSubtractsButNeverGoesNegative() throws Exception {
        UUID productId = newProduct(newRestaurant(), 5);

        adjustStock(productId, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(15));
        adjustStock(productId, -15)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(0));
        adjustStock(productId, -1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        adjustStock(productId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STOCK_ADJUSTMENT"));
        adjustStock(UUID.randomUUID(), 1)
                .andExpect(status().isNotFound());
    }

    /**
     * Stock adjustment không tăng version, nên Admin đang sửa giá (giữ version cũ) vẫn sửa được,
     * và việc sửa giá KHÔNG ghi đè stock vừa được nhập thêm.
     */
    @Test
    void updatingProductInfoDoesNotOverwriteStock() throws Exception {
        UUID productId = newProduct(newRestaurant(), 5);

        adjustStock(productId, 7).andExpect(status().isOk());

        updateProduct(productId, "Tên mới", "99000", "ACTIVE", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(12))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "select available_stock from products where id = ?", Integer.class, productId))
                .isEqualTo(12);
    }

    // ---------- authorization ----------

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/restaurants"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/restaurants").header("Authorization", auth.customerBearer()))
                .andExpect(status().isForbidden());

        UUID owner = auth.createUser(Role.RESTAURANT).id();
        mockMvc.perform(post("/api/v1/admin/restaurants")
                        .header("Authorization", auth.customerBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ownerUserId", owner, "name", "X", "address", "Y"))))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private UUID newRestaurant() throws Exception {
        JsonNode body = readJson(createRestaurant(auth.createUser(Role.RESTAURANT).id(), "Quán " + UUID.randomUUID())
                .andExpect(status().isCreated()));
        return UUID.fromString(body.get("id").asText());
    }

    private UUID newProduct(UUID restaurantId, int stock) throws Exception {
        JsonNode body = readJson(createProduct(restaurantId, "Phở", "50000", stock).andExpect(status().isCreated()));
        return UUID.fromString(body.get("id").asText());
    }

    private void deactivateRestaurant(UUID restaurantId) throws Exception {
        JsonNode current = readJson(adminRequest(get("/api/v1/admin/restaurants/{id}", restaurantId)));
        updateRestaurant(restaurantId, current.get("name").asText(), "INACTIVE", current.get("version").asLong())
                .andExpect(status().isOk());
    }

    private ResultActions createRestaurant(UUID ownerUserId, String name) throws Exception {
        return adminRequest(post("/api/v1/admin/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("ownerUserId", ownerUserId, "name", name, "address", "1 Tràng Tiền"))));
    }

    private ResultActions updateRestaurant(UUID restaurantId, String name, String status, long version)
            throws Exception {
        return adminRequest(put("/api/v1/admin/restaurants/{id}", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "address", "1 Tràng Tiền", "status", status, "version", version))));
    }

    private ResultActions createProduct(UUID restaurantId, String name, String price, int initialStock)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Món ngon");
        body.put("price", new BigDecimal(price));
        body.put("initialStock", initialStock);
        return adminRequest(post("/api/v1/admin/restaurants/{id}/products", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions updateProduct(UUID productId, String name, String price, String status, long version)
            throws Exception {
        return adminRequest(put("/api/v1/admin/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "price", new BigDecimal(price),
                        "status", status, "version", version))));
    }

    private ResultActions adjustStock(UUID productId, int delta) throws Exception {
        return adminRequest(post("/api/v1/admin/products/{id}/stock-adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("delta", delta))));
    }

    private ResultActions adminRequest(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", admin));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
