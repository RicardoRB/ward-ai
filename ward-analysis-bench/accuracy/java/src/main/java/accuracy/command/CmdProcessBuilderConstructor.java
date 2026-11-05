package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdProcessBuilderConstructor {
  void run(HttpServletRequest request) throws Exception {
    String tool = request.getParameter("tool");
    new ProcessBuilder(tool).start();
  }
}
