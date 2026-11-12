import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

class Security {
  void classic(HttpSecurity http) throws Exception {
    http.csrf().disable();
  }

  void lambda(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable);
  }

  void inline(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable());
  }
}
