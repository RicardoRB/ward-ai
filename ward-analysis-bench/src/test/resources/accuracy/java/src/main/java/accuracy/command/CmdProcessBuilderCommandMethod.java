package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdProcessBuilderCommandMethod {
  void run(HttpServletRequest request) throws Exception {
    String arg = request.getQueryString();
    ProcessBuilder builder = new ProcessBuilder();
    builder.command(arg).start();
  }
}
