import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

class Security {
  void defaults(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/webhook"));
  }

  // A stateless chain with a token filter has no session cookie for a browser to send.
  void stateless(HttpSecurity http, TokenFilter filter) throws Exception {
    http.csrf().disable().sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
  }

  void statelessPolicyFirst(HttpSecurity http) throws Exception {
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
        .csrf().disable().authorizeRequests().anyRequest().authenticated();
  }

  void statelessLambda(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }

  void statelessReference(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS));
  }

  HttpSecurity statelessLocal(HttpSecurity http) throws Exception {
    HttpSecurity configured =
        http.csrf().disable().sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS).and();
    return configured;
  }
}
