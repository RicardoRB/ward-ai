import java.net.URI;
import java.net.URL;

class Endpoints {
  Object url() throws Exception {
    return new URL("http://api.example.com/v1/items");
  }

  Object uri() {
    return URI.create("ftp://files.example.com/report.csv");
  }
}
