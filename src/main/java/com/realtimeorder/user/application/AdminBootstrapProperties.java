package com.realtimeorder.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tài khoản ADMIN được tạo lúc khởi động (ADR-035).
 *
 * Để trống {@code email} thì bỏ qua bootstrap. Không có giá trị mặc định cho password,
 * để không môi trường nào vô tình chạy với một admin password ai cũng biết.
 *
 * @param email    email của admin, để trống để tắt bootstrap
 * @param password password của admin, bắt buộc khi có email
 * @param name     tên hiển thị
 */
@ConfigurationProperties(prefix = "bootstrap.admin")
public record AdminBootstrapProperties(String email, String password, String name) {

    public AdminBootstrapProperties {
        if (email != null && !email.isBlank()) {
            if (password == null || password.length() < 8 || password.length() > 72) {
                throw new IllegalArgumentException("bootstrap.admin.password phải có 8–72 ký tự khi đã cấu hình email");
            }
            if (name == null || name.isBlank()) {
                name = "Administrator";
            }
        }
    }

    public boolean enabled() {
        return email != null && !email.isBlank();
    }
}
