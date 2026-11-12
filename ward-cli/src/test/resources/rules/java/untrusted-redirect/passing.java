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

  // The limit: a value that leaves the method is not followed.
  void crossMethod(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String next = read(request);
    response.sendRedirect(next);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("next");
  }
}
