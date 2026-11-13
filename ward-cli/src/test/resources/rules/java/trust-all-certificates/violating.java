import javax.net.ssl.X509TrustManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;

class Trust {
  Object builder(SSLContextBuilder builder) throws Exception {
    return builder.loadTrustMaterial(null, (chain, authType) -> true);
  }

  Object strategy() {
    return new TrustSelfSignedStrategy();
  }

  X509TrustManager manager() {
    return new X509TrustManager() {
      public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String type) {}

      public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String type) {}

      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[0];
      }
    };
  }
}
