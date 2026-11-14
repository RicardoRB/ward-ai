import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

class Cookies {
  void session(HttpServletResponse response) {
    Cookie cookie = new Cookie("id", "1");
    cookie.setHttpOnly(true);
    response.addCookie(cookie);
  }
}
