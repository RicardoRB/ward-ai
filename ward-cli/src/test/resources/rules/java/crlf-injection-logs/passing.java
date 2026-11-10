import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response, Logger logger) throws Exception {
    String value = request.getParameter("v");
    logger.info("user");
  }
}
