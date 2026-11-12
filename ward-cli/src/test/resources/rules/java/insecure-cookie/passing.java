import javax.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;

class Cookies {
  void harden(Cookie cookie) {
    cookie.setSecure(true);
    cookie.setHttpOnly(true);
  }

  Object response() {
    return ResponseCookie.from("id", "1").secure(true).httpOnly(true);
  }
}
