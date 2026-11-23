import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
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

  // The values only fill the query or the fragment of a URL whose scheme and host are constants.
  Object queryOnly(HttpServletRequest request, RestTemplate rest) {
    String v = request.getParameter("q");
    String url = "https://api.example.com/search?q=" + URLEncoder.encode(v, StandardCharsets.UTF_8);
    return rest.getForObject(url, String.class);
  }

  Object queryInline(HttpServletRequest request) throws Exception {
    String v = request.getParameter("q");
    return new URL("https://api.example.com/search?q=" + v + "&lang=en").openStream();
  }

  Object fragment(HttpServletRequest request, RestTemplate rest) {
    String v = request.getParameter("q");
    return rest.getForObject("https://api.example.com/page#" + v, String.class);
  }

  Object queryLocal(HttpServletRequest request, RestTemplate rest) {
    String encoded = URLEncoder.encode(request.getParameter("q"), StandardCharsets.UTF_8);
    return rest.getForObject("https://api.example.com/search?q=" + encoded, String.class);
  }

  Object queryFormatted(HttpServletRequest request, RestTemplate rest) {
    String v = request.getParameter("q");
    rest.getForObject(String.format("https://api.example.com/search?q=%s", v), String.class);
    return rest.getForObject("https://api.example.com/search?q=%s".formatted(v), String.class);
  }

  Object queryHelper(HttpServletRequest request, RestTemplate rest) {
    return rest.getForObject(searchUrl(request.getParameter("q")), String.class);
  }

  String searchUrl(String v) {
    return "https://api.example.com/search?q=" + URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  // The allow-list check is in a method that the caller uses.
  void requireKnown(String provider) {
    if (!PROVIDERS.contains(provider)) {
      throw new IllegalArgumentException("unsupported");
    }
  }

  Object calledGuard(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    requireKnown(provider);
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  Object calledGuardThis(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    this.requireKnown(provider);
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  String authorize(String provider) {
    if (!PROVIDERS.contains(provider)) {
      throw new IllegalArgumentException("unsupported");
    }
    return "https://login.example.com/" + provider;
  }

  Object calledGuardReturning(HttpServletRequest request, RestTemplate rest) {
    String url = authorize(request.getParameter("provider"));
    return rest.getForObject(url, String.class);
  }
}
