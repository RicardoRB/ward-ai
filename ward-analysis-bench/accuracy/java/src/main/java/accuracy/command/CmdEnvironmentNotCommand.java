package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdEnvironmentNotCommand {
  void run(HttpServletRequest request) throws Exception {
    String home = request.getParameter("home");
    ProcessBuilder builder = new ProcessBuilder("env");
    builder.environment().put("HOME", home);
    builder.start();
  }
}
