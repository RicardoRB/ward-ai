import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;
import java.util.HashSet;
import java.util.Set;

class Fetcher {
  Object direct(HttpServletRequest request) throws Exception {
    return new URL(request.getParameter("url")).openStream();
  }

  Object propagated(HttpServletRequest request, RestTemplate rest) {
    String host = request.getParameter("host");
    String address = "http://" + host + "/status";
    return rest.getForObject(address, String.class);
  }

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, RestTemplate rest) {
    String address = read(request);
    return rest.getForObject(address, String.class);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("url");
  }

  private static final Set<String> PROVIDERS = Set.of("google", "github");
  private Set<String> open = new HashSet<>();

  // The collection can change, so a value in it is not one of the constants.
  Object changing(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    if (!open.contains(provider)) {
      throw new IllegalArgumentException("unsupported");
    }
    return rest.getForObject("https://login.example.com?provider=" + provider, String.class);
  }

  // The then-branch can continue, so the value is not checked past the guard.
  Object continuing(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    if (!PROVIDERS.contains(provider)) {
      System.out.println("unsupported");
    }
    return rest.getForObject("https://login.example.com?provider=" + provider, String.class);
  }

  // The guard checks another value than the one that is used.
  Object other(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    String name = request.getParameter("name");
    if (!PROVIDERS.contains(name)) {
      throw new IllegalArgumentException("unsupported");
    }
    return rest.getForObject("https://login.example.com?provider=" + provider, String.class);
  }
}
