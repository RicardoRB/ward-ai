import java.io.File;
import javax.ws.rs.QueryParam;

class Check {
  void run(@QueryParam("v") String value) throws Exception {
    new File("/data/" + value);
  }
}
