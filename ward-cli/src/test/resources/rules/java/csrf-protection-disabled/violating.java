import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

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

  // A stateless policy does not help when the same chain logs in with a cookie.
  void statelessWithFormLogin(HttpSecurity http) throws Exception {
    http.csrf().disable().sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().formLogin();
  }

  void statelessWithBasic(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).httpBasic(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }

  void sessionKept(HttpSecurity http) throws Exception {
    http.csrf().disable().sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
  }

  // The policy is in another statement, so this chain keeps the default session.
  void policyElsewhere(HttpSecurity http) throws Exception {
    http.csrf().disable();
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }
}
