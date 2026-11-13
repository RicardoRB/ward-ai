import javax.net.ssl.TrustManagerFactory;
import org.apache.http.ssl.SSLContextBuilder;

class Trust {
  Object builder(SSLContextBuilder builder, java.security.KeyStore store) throws Exception {
    return builder.loadTrustMaterial(store, null);
  }

  Object factory() throws Exception {
    return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  }
}
