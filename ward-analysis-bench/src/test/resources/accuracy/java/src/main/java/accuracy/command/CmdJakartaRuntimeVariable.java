package accuracy.command;

import jakarta.servlet.http.HttpServletRequest;

class CmdJakartaRuntimeVariable {
  void run(HttpServletRequest request) throws Exception {
    String name = request.getHeader("X-Tool");
    Runtime runtime = Runtime.getRuntime();
    runtime.exec(name);
  }
}
