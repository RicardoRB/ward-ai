import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

class Launch {
  void builder(HttpServletRequest request, ProcessBuilder builder) {
    builder.environment().put("LANG", request.getParameter("lang"));
  }

  void runtime(HttpServletRequest request) throws IOException {
    String[] env = {"HOME=" + request.getParameter("home")};
    Runtime.getRuntime().exec("tool", env);
  }

  void viaFile(ProcessBuilder builder) {
    builder.environment().put("TOKEN", System.getenv("INPUT"));
  }
}
