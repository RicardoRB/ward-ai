import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

class Verifiers {
  void strict(HttpsURLConnection connection, HostnameVerifier verifier) {
    connection.setHostnameVerifier(verifier);
  }

  void defaults() {
    HttpsURLConnection.setDefaultHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
  }
}
