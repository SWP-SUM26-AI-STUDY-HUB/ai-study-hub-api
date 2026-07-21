package vn.ai_study_hub_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("Ensuring PostgreSQL 'unaccent' extension is created...");
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            log.info("PostgreSQL 'unaccent' extension is ready.");
        } catch (Exception e) {
            log.error("Failed to create 'unaccent' extension. Depending on database setup, search accuracy may be degraded.", e);
        }
    }
}
