package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdHelperMethodMiss {
  void handle(HttpServletRequest request) throws Exception {
    launch(request.getParameter("cmd"));
  }

  private void launch(String cmd) throws Exception {
    Runtime.getRuntime().exec(cmd);
  }
}
