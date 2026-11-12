import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class Cors {
  void registry(CorsRegistry registry) {
    registry.addMapping("/api/**").allowedOrigins("*");
  }

  void configuration(CorsConfiguration configuration) {
    configuration.addAllowedOrigin("*");
  }

  void header(javax.servlet.http.HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
  }
}
