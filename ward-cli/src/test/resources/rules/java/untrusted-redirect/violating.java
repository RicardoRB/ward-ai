import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Login {
  void direct(HttpServletRequest request, HttpServletResponse response) throws Exception {
    response.sendRedirect(request.getParameter("next"));
  }

  void propagated(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String next = request.getParameter("next");
    String target = "https://" + next;
    response.sendRedirect(target);
  }

  // A value that another method of the file returns is followed.
  void crossMethod(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String next = read(request);
    response.sendRedirect(next);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("next");
  }
}
