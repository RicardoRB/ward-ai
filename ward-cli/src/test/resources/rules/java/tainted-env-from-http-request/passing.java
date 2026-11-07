import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String value = request.getParameter("v");
    Runtime.getRuntime().exec("ls", new String[] {"A=1"});
  }
}
