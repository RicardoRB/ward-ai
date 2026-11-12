import javax.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;

class Cookies {
  void secure(Cookie cookie) {
    cookie.setSecure(false);
  }

  void script(Cookie cookie) {
    cookie.setHttpOnly(false);
  }

  Object response() {
    return ResponseCookie.from("id", "1").httpOnly(false);
  }
}
