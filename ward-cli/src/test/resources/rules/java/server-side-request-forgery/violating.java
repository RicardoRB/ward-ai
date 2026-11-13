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

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, RestTemplate rest) {
    String address = read(request);
    return rest.getForObject(address, String.class);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("url");
  }
}
