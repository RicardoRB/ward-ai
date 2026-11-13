import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class Cors {
  void registry(CorsRegistry registry) {
    registry.addMapping("/api/**").allowedOrigins("https://app.example.com");
  }

  void configuration(CorsConfiguration configuration) {
    configuration.addAllowedOrigin("https://app.example.com");
  }
}
