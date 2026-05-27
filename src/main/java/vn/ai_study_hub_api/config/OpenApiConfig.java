package vn.ai_study_hub_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI myOpenAPI() {
        // Cấu hình server Local
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Server");

        // Cấu hình server VPS
        Server vpsServer = new Server();
        vpsServer.setUrl("http://14.225.254.145:8080");
        vpsServer.setDescription("VPS Server");

        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("AI Study Hub API")
                        .version("1.0")
                        .description("API DOCUMENTATION"))
                .servers(List.of(localServer, vpsServer));
    }
}