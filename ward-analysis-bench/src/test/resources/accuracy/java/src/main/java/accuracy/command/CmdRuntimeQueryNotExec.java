package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdRuntimeQueryNotExec {
  void run(HttpServletRequest request) {
    int threads = Integer.parseInt(request.getParameter("threads"));
    System.out.println(Runtime.getRuntime().availableProcessors() + threads);
  }
}
