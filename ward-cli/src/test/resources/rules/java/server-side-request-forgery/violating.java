import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;

class Fetcher {
  Object direct(HttpServletRequest request) throws Exception {
    return new URL(request.getParameter("url")).openStream();
  }

  Object propagated(HttpServletRequest request, RestTemplate rest) {
    String host = request.getParameter("host");
    String address = "http://" + host + "/status";
    return rest.getForObject(address, String.class);
  }
}
