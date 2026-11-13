import javax.servlet.http.HttpServletRequest;

class Plugins {
  Object constant() throws Exception {
    return Class.forName("com.example.plugins.Default");
  }

  Object numeric(HttpServletRequest request) throws Exception {
    int version = Integer.parseInt(request.getParameter("version"));
    return Class.forName("com.example.plugins.V" + version);
  }
}
