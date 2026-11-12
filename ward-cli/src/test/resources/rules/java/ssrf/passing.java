import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;

class Fetcher {
  Object constant(RestTemplate rest) {
    return rest.getForObject("https://api.example.com/status", String.class);
  }

  Object numeric(HttpServletRequest request) throws Exception {
    long id = Long.parseLong(request.getParameter("id"));
    return new URL("https://api.example.com/items/" + id).openStream();
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request, RestTemplate rest) {
    String address = read(request);
    return rest.getForObject(address, String.class);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("url");
  }
}
