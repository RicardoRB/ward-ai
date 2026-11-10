import java.nio.file.Paths;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String value = request.getParameter("v");
    Paths.get("x");
  }
}
