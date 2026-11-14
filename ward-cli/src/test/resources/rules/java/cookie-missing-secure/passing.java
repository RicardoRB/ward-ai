import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

class Cookies {
  void session(HttpServletResponse response) {
    Cookie cookie = new Cookie("id", "1");
    cookie.setSecure(true);
    response.addCookie(cookie);
  }

  void other(HttpServletResponse response, Cookie first, Cookie second) {
    first.setSecure(true);
    response.addCookie(first);
  }
}
