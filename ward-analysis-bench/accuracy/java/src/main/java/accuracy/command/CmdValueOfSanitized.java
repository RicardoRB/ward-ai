package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdValueOfSanitized {
  void run(HttpServletRequest request) throws Exception {
    Integer port = Integer.valueOf(request.getParameter("port"));
    new ProcessBuilder("nc", "-z", "localhost", port.toString()).start();
  }
}
