import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

class Login {
  void constant(HttpSession session) {
    session.setAttribute("role", "guest");
  }

  void numeric(HttpServletRequest request, HttpSession session) {
    int id = Integer.parseInt(request.getParameter("id"));
    session.setAttribute("id", id);
  }
}
