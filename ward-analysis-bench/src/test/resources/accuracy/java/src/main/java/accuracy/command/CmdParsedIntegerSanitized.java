package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdParsedIntegerSanitized {
  void run(HttpServletRequest request) throws Exception {
    int lines = Integer.parseInt(request.getParameter("lines"));
    Runtime.getRuntime().exec("head -n " + lines + " app.log");
  }
}
