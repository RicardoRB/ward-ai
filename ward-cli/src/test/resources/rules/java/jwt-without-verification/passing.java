import io.jsonwebtoken.Jwts;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

class Tokens {
  Object signed(String token, java.security.Key key) {
    return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
  }

  Object verified(String token, Algorithm algorithm) {
    return JWT.require(algorithm).build().verify(token);
  }
}
