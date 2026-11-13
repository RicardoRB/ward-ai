import io.jsonwebtoken.Jwts;
import com.auth0.jwt.JWT;

class Tokens {
  Object unsigned(String token) {
    return Jwts.parser().setSigningKey("k").parseClaimsJwt(token);
  }

  Object decoded(String token) {
    return JWT.decode(token);
  }
}
