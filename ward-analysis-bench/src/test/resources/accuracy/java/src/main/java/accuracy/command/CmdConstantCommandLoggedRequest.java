package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdConstantCommandLoggedRequest {
  void run(HttpServletRequest request) throws Exception {
    System.out.println(request.getParameter("note"));
    Runtime.getRuntime().exec("ls -l");
  }
}
