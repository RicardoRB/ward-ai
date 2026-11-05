package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdParameterValuesArray {
  void run(HttpServletRequest request) throws Exception {
    String[] args = request.getParameterValues("arg");
    Runtime.getRuntime().exec(args);
  }
}
