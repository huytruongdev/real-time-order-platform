package com.realtimeorder.user.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Tạo ADMIN đầu tiên khi application khởi động (sau khi Flyway migration đã chạy).
 *
 * Không seed admin bằng Flyway: migration sẽ chứa cứng password hash và chạy giống nhau
 * trên mọi môi trường.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminBootstrapProperties properties;
    private final UserAdminService userAdminService;

    AdminBootstrap(AdminBootstrapProperties properties, UserAdminService userAdminService) {
        this.properties = properties;
        this.userAdminService = userAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Bỏ qua admin bootstrap: bootstrap.admin.email chưa được cấu hình");
            return;
        }
        if (userAdminService.ensureAdmin(properties.email(), properties.password(), properties.name())) {
            log.info("Đã tạo admin {}", properties.email());
        } else {
            log.info("Email admin {} đã tồn tại, bỏ qua bootstrap", properties.email());
        }
    }
}
