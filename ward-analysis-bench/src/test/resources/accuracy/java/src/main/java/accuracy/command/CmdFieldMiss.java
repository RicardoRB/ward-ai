package accuracy.command;

import javax.servlet.http.HttpServletRequest;

class CmdFieldMiss {
  private String program;

  void capture(HttpServletRequest request) {
    this.program = request.getParameter("program");
  }

  void run() throws Exception {
    new ProcessBuilder(this.program).start();
  }
}
