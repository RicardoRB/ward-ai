import javax.servlet.http.HttpServletRequest;
import org.apache.commons.exec.CommandLine;

class Run {
  void run(HttpServletRequest request) throws Exception {
    String cmd = request.getParameter("cmd");
    Runtime.getRuntime().exec(cmd);
  }
}

class Exec {
  Object parse(HttpServletRequest request) {
    String cmd = request.getParameter("cmd");
    return CommandLine.parse(cmd);
  }
}
