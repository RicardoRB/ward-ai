import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

class Protocols {
  SSLContext context() throws Exception {
    return SSLContext.getInstance("TLSv1.3");
  }

  void socket(SSLSocket socket) {
    socket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
  }
}
