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
}
