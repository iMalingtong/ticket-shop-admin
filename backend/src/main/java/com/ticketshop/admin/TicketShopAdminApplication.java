package com.ticketshop.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TicketShopAdminApplication {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--migrate".equals(args[0])) {
            com.ticketshop.admin.operations.DatabaseMigrations.run(System.getenv());
            return;
        }
        if (args.length == 1 && "--bootstrap-admin".equals(args[0])) {
            com.ticketshop.admin.operations.BootstrapAdmin.run();
            return;
        }
        SpringApplication.run(TicketShopAdminApplication.class, args);
    }
}
