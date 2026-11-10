import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws Exception {
    String value = request.getParameter("v");
    session.setAttribute("k", value);
  }
}
