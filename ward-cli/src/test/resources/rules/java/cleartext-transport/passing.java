import java.net.URI;
import java.net.URL;

class Endpoints {
  Object secure() throws Exception {
    return new URL("https://api.example.com/v1/items");
  }

  Object local() {
    return URI.create("http://localhost:8080/health");
  }
}
