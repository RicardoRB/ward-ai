import java.io.IOException;
import javax.servlet.http.HttpServletRequest;

class Launch {
  void constant(ProcessBuilder builder) {
    builder.environment().put("LANG", "C");
  }

  void runtime() throws IOException {
    Runtime.getRuntime().exec("tool", new String[] {"HOME=/srv"});
  }

  void ignoredInput(HttpServletRequest request, ProcessBuilder builder) {
    String unused = request.getParameter("lang");
    builder.environment().put("LANG", "C");
  }
}
