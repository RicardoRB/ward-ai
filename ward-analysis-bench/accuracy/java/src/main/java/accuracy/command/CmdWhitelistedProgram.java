package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdWhitelistedProgram {
  void run(HttpServletRequest request) throws Exception {
    String tool = request.getParameter("tool");
    String safe = "git".equals(tool) ? "git" : "ls";
    Runtime.getRuntime().exec(safe);
  }
}
