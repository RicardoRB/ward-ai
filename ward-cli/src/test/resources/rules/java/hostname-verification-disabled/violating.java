import javax.net.ssl.HttpsURLConnection;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

class Verifiers {
  void lambda(HttpsURLConnection connection) {
    connection.setHostnameVerifier((host, session) -> true);
  }

  void global() {
    HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
  }

  Object client(org.apache.http.impl.client.HttpClientBuilder builder) {
    return builder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
  }
}
