import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

class Login {
  void stored(HttpServletRequest request, HttpSession session) {
    session.setAttribute("role", request.getParameter("role"));
  }

  void chained(HttpServletRequest request) {
    request.getSession().setAttribute("user", request.getParameter("user"));
  }
}
