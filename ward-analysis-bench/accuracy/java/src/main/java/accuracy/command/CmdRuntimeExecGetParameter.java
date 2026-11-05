package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdRuntimeExecGetParameter {
  void run(HttpServletRequest request) throws Exception {
    String cmd = request.getParameter("cmd");
    Runtime.getRuntime().exec(cmd);
  }
}
