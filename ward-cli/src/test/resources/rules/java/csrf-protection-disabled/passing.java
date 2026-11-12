import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class Security {
  void defaults(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/webhook"));
  }
}
