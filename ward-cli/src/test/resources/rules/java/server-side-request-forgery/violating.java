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
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  // The then-branch can continue, so the value is not checked past the guard.
  Object continuing(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    if (!PROVIDERS.contains(provider)) {
      System.out.println("unsupported");
    }
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  // The guard checks another value than the one that is used.
  Object other(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    String name = request.getParameter("name");
    if (!PROVIDERS.contains(name)) {
      throw new IllegalArgumentException("unsupported");
    }
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  // A value before the ? or in the host can still change the address.
  Object hostBeforeQuery(HttpServletRequest request) throws Exception {
    String host = request.getParameter("host");
    return new URL("https://" + host + "/search?q=1").openStream();
  }

  Object pathBeforeQuery(HttpServletRequest request, RestTemplate rest) {
    String path = request.getParameter("path");
    return rest.getForObject("https://api.example.com/" + path + "?q=1", String.class);
  }

  Object baseBeforeQuery(HttpServletRequest request, RestTemplate rest) {
    String base = request.getParameter("base");
    return rest.getForObject(base + "?q=1", String.class);
  }

  Object hostInFormat(HttpServletRequest request, RestTemplate rest) {
    String host = request.getParameter("host");
    return rest.getForObject(String.format("https://%s/search?q=%s", host, "x"), String.class);
  }

  // A method that does not stop on a value outside the allow-list does not check it.
  void logOnly(String provider) {
    if (!PROVIDERS.contains(provider)) {
      System.out.println("unsupported");
    }
  }

  Object calledLogOnly(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    logOnly(provider);
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  void returnOnly(String provider) {
    if (!PROVIDERS.contains(provider)) {
      return;
    }
  }

  Object calledReturnOnly(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    returnOnly(provider);
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  void checkOpen(String provider) {
    if (!open.contains(provider)) {
      throw new IllegalArgumentException("unsupported");
    }
  }

  Object calledOpen(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    checkOpen(provider);
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  void checkSecond(String first, String second) {
    if (!PROVIDERS.contains(second)) {
      throw new IllegalArgumentException("unsupported");
    }
  }

  Object calledOtherArgument(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    checkSecond(provider, "google");
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  void checkLate(String provider, boolean skip) {
    if (skip) {
      return;
    }
    if (!PROVIDERS.contains(provider)) {
      throw new IllegalArgumentException("unsupported");
    }
  }

  Object calledLate(HttpServletRequest request, RestTemplate rest) {
    String provider = request.getParameter("provider");
    checkLate(provider, true);
    return rest.getForObject("https://login.example.com/" + provider, String.class);
  }

  String addressOf(String provider) {
    return "https://login.example.com/" + provider;
  }

  Object calledWithoutCheck(HttpServletRequest request, RestTemplate rest) {
    return rest.getForObject(addressOf(request.getParameter("provider")), String.class);
  }
}
