package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdUnrelatedExecType {
  void run(HttpServletRequest request, Launcher launcher) {
    String cmd = request.getParameter("cmd");
    launcher.exec(cmd);
  }
}

class Launcher {
  void exec(String command) {}
}
