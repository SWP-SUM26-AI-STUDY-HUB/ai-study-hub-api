package vn.ai_study_hub_api.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    @Bean
    public Module javaTimeModule() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
        return module;
    }
}
