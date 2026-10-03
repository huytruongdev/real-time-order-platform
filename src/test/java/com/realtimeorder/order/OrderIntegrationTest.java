package com.realtimeorder.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.catalog.application.CatalogAdminService;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.support.CatalogFixtures;
import com.realtimeorder.support.CatalogFixtures.RestaurantFixture;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3: Order flow qua HTTP với PostgreSQL thật.
 * Mỗi test tạo restaurant, product, customer riêng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserAdminService userAdminService;
    @Autowired
    CatalogAdminService catalogAdminService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    TestAuth auth;
    CatalogFixtures fixtures;

    RestaurantFixture restaurant;
    String restaurantBearer;
    UUID pho;
    UUID bunCha;
    String customer;

    @BeforeEach
    void setUp() throws Exception {
        auth = new TestAuth(mockMvc, objectMapper, userAdminService);
        fixtures = new CatalogFixtures(userAdminService, catalogAdminService);

        restaurant = fixtures.newRestaurant();
        restaurantBearer = auth.bearer(restaurant.owner().email());
        pho = fixtures.newProduct(restaurant.id(), "Phở bò", "55000", 10);
        bunCha = fixtures.newProduct(restaurant.id(), "Bún chả", "45000", 5);
        customer = auth.customerBearer();
    }

    // ---------- create ----------

    @Test
    void placeOrderReservesStockAndUsesServerSidePrices() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 2, bunCha, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.cancelReason").isEmpty())
                .andExpect(jsonPath("$.totalAmount").value(155000.00))
                .andExpect(jsonPath("$.items.length()").value(2))
                // items sort theo tên
                .andExpect(jsonPath("$.items[0].productName").value("Bún chả"))
                .andExpect(jsonPath("$.items[1].subtotal").value(110000.00)));

        assertStock(pho, 8, 2);
        assertStock(bunCha, 4, 1);
        assertThat(historyOf(order)).containsExactly("null->CREATED by CUSTOMER");
    }

    @Test
    void priceChangeAfterOrderDoesNotChangeOrderSnapshot() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 1)).andExpect(status().isCreated()));

        var view = catalogAdminService.getProduct(pho);
        catalogAdminService.updateProduct(pho, "Phở bò đặc biệt", null, new BigDecimal("99000"),
                CatalogStatus.ACTIVE, view.version());

        getOrder(customer, order).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Phở bò"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(55000.00))
                .andExpect(jsonPath("$.totalAmount").value(55000.00));
    }

    @Test
    void placeOrderWithInsufficientStockCreatesNothing() throws Exception {
        placeOrder(customer, restaurant.id(), Map.of(pho, 2, bunCha, 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertStock(pho, 10, 0);
        assertStock(bunCha, 5, 0);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders where restaurant_id = ?", Integer.class, restaurant.id())).isZero();
    }

    @Test
    void placeOrderValidatesInput() throws Exception {
        UUID otherRestaurantProduct = fixtures.newProduct(fixtures.newRestaurant().id(), "Món lạ", "10000", 5);

        placeOrder(customer, restaurant.id(), Map.of(otherRestaurantProduct, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));

        placeOrder(customer, UUID.randomUUID(), Map.of(pho, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));

        placeOrder(customer, restaurant.id(), Map.of(pho, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        placeOrder(customer, restaurant.id(), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Trùng productId: không thể dùng Map, gửi JSON trực tiếp.
        String duplicated = objectMapper.writeValueAsString(Map.of(
                "restaurantId", restaurant.id(),
                "items", List.of(Map.of("productId", pho, "quantity", 1), Map.of("productId", pho, "quantity", 2))));
        mockMvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicated))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ORDER_ITEM"));
    }

    // ---------- read ----------

    @Test
    void customerSeesOnlyOwnOrders() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 1)).andExpect(status().isCreated()));
        String otherCustomer = auth.customerBearer();

        getOrder(customer, order).andExpect(status().isOk());
        getOrder(otherCustomer, order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/orders").header("Authorization", customer))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/orders").header("Authorization", otherCustomer))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------- customer cancel ----------

    @Test
    void customerCancelReleasesReservation() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 3)).andExpect(status().isCreated()));

        cancel(customer, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("CUSTOMER_CANCELLED"));

        assertStock(pho, 10, 0);
        cancel(customer, order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        assertStock(pho, 10, 0);
    }

    @Test
    void customerCanCancelConfirmedOrder() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 3)).andExpect(status().isCreated()));
        confirm(restaurantBearer, order).andExpect(status().isOk());

        cancel(customer, order).andExpect(status().isOk());

        assertStock(pho, 10, 0);
        assertThat(historyOf(order)).containsExactly(
                "null->CREATED by CUSTOMER",
                "CREATED->CONFIRMED by RESTAURANT",
                "CONFIRMED->CANCELLED by CUSTOMER");
    }

    @Test
    void otherCustomerCannotCancel() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 1)).andExpect(status().isCreated()));

        cancel(auth.customerBearer(), order).andExpect(status().isNotFound());

        assertStock(pho, 9, 1);
    }

    // ---------- restaurant ----------

    @Test
    void restaurantConfirmKeepsReservation() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 2)).andExpect(status().isCreated()));

        confirm(restaurantBearer, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertStock(pho, 8, 2);
    }

    @Test
    void restaurantRejectReleasesReservation() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 2)).andExpect(status().isCreated()));

        reject(restaurantBearer, order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("RESTAURANT_REJECTED"));

        assertStock(pho, 10, 0);
    }

    @Test
    void restaurantCannotRejectConfirmedOrder() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 2)).andExpect(status().isCreated()));
        confirm(restaurantBearer, order).andExpect(status().isOk());

        reject(restaurantBearer, order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
        confirm(restaurantBearer, order).andExpect(status().isConflict());

        assertStock(pho, 8, 2);
    }

    @Test
    void restaurantSeesAndHandlesOnlyOwnOrders() throws Exception {
        JsonNode order = readJson(placeOrder(customer, restaurant.id(), Map.of(pho, 1)).andExpect(status().isCreated()));
        String otherOwner = auth.bearer(fixtures.newRestaurant().owner().email());
        String ownerWithoutRestaurant = auth.bearer(auth.createUser(Role.RESTAURANT).email());

        mockMvc.perform(get("/api/v1/restaurant/orders").param("status", "CREATED")
                        .header("Authorization", restaurantBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(order.get("id").asText()));
        mockMvc.perform(get("/api/v1/restaurant/orders").param("status", "CONFIRMED")
                        .header("Authorization", restaurantBearer))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/restaurant/orders").header("Authorization", otherOwner))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/restaurant/orders").header("Authorization", ownerWithoutRestaurant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        confirm(otherOwner, order).andExpect(status().isNotFound());
        reject(otherOwner, order).andExpect(status().isNotFound());
    }

    // ---------- authorization ----------

    @Test
    void endpointsAreRestrictedByRole() throws Exception {
        placeOrder(restaurantBearer, restaurant.id(), Map.of(pho, 1)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/restaurant/orders").header("Authorization", customer))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private ResultActions placeOrder(String bearer, UUID restaurantId, Map<UUID, Integer> lines) throws Exception {
        List<Map<String, Object>> items = lines.entrySet().stream()
                .map(e -> Map.<String, Object>of("productId", e.getKey(), "quantity", e.getValue()))
                .toList();
        return mockMvc.perform(post("/api/v1/orders")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("restaurantId", restaurantId, "items", items))));
    }

    private ResultActions getOrder(String bearer, JsonNode order) throws Exception {
        return mockMvc.perform(get("/api/v1/orders/{id}", id(order)).header("Authorization", bearer));
    }

    private ResultActions cancel(String bearer, JsonNode order) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/{id}/cancel", id(order)).header("Authorization", bearer));
    }

    private ResultActions confirm(String bearer, JsonNode order) throws Exception {
        return mockMvc.perform(post("/api/v1/restaurant/orders/{id}/confirm", id(order)).header("Authorization", bearer));
    }

    private ResultActions reject(String bearer, JsonNode order) throws Exception {
        return mockMvc.perform(post("/api/v1/restaurant/orders/{id}/reject", id(order)).header("Authorization", bearer));
    }

    private void assertStock(UUID productId, int available, int reserved) {
        var row = jdbcTemplate.queryForMap(
                "select available_stock, reserved_stock from products where id = ?", productId);
        assertThat(row.get("available_stock")).as("available_stock").isEqualTo(available);
        assertThat(row.get("reserved_stock")).as("reserved_stock").isEqualTo(reserved);
    }

    private List<String> historyOf(JsonNode order) {
        return jdbcTemplate.queryForList("""
                select coalesce(old_status, 'null') || '->' || new_status || ' by ' || actor
                  from order_status_history
                 where order_id = ?
                 order by created_at, id
                """, String.class, id(order));
    }

    private static UUID id(JsonNode order) {
        return UUID.fromString(order.get("id").asText());
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
