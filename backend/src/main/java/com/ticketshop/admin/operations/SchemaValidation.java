package com.ticketshop.admin.operations;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runtime DML credentials validate the already-migrated schema; never migrate on HTTP startup. */
@Component
public class SchemaValidation implements ApplicationRunner {
    private final DataSource dataSource;
    public SchemaValidation(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public void run(ApplicationArguments args) { DatabaseMigrations.validateCurrent(dataSource); }
}
