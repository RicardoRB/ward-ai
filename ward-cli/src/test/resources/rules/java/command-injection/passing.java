import javax.servlet.http.HttpServletRequest;

class Run {
  void run(HttpServletRequest request) throws Exception {
    String cmd = request.getParameter("cmd");
    Runtime.getRuntime().exec("ls");
  }
}
