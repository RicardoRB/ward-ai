import javax.servlet.http.HttpServletRequest;

class Plugins {
  Object constant() throws Exception {
    return Class.forName("com.example.plugins.Default");
  }

  Object numeric(HttpServletRequest request) throws Exception {
    int version = Integer.parseInt(request.getParameter("version"));
    return Class.forName("com.example.plugins.V" + version);
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request) throws Exception {
    String type = read(request);
    return Class.forName(type);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("type");
  }
}
