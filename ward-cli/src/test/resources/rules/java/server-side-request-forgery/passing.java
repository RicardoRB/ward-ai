import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;
import java.util.Set;

class Fetcher {
  Object constant(RestTemplate rest) {
    return rest.getForObject("https://api.example.com/status", String.class);
  }

  Object numeric(HttpServletRequest request) throws Exception {
    long id = Long.parseLong(request.getParameter("id"));
    return new URL("https://api.example.com/items/" + id).openStream();
  }

  private static final Set<String> PROVIDERS = Set.of("google", "github");

  // Past the allow-list guard the value is one of the constants.
  Object allowed(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    if (!PROVIDERS.contains(provider)) {
      throw new IllegalArgumentException("unsupported");
    }
    return rest.getForObject("https://login.example.com?provider=" + provider, String.class);
  }

  Object allowedParameter(String provider, RestTemplate rest) {
    if (!PROVIDERS.contains(provider)) throw new IllegalArgumentException("unsupported");
    return rest.getForObject("https://login.example.com?provider=" + provider, String.class);
  }
}
