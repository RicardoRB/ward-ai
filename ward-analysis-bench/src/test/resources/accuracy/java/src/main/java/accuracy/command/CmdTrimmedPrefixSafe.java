package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdTrimmedPrefixSafe {
  void run(HttpServletRequest request) throws Exception {
    String name = request.getParameter("name");
    if (name.length() > 3) {
      return;
    }
    Runtime.getRuntime().exec("true");
  }
}
