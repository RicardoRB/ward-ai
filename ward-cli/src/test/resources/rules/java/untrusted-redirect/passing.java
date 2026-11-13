import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Login {
  void constant(HttpServletResponse response) throws Exception {
    response.sendRedirect("/home");
  }

  void numeric(HttpServletRequest request, HttpServletResponse response) throws Exception {
    int page = Integer.parseInt(request.getParameter("page"));
    response.sendRedirect("/page/" + page);
  }
}
