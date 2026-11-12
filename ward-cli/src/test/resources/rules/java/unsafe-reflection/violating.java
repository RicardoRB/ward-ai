import javax.servlet.http.HttpServletRequest;

class Plugins {
  Object direct(HttpServletRequest request) throws Exception {
    return Class.forName(request.getParameter("type"));
  }

  Object propagated(HttpServletRequest request, java.lang.ClassLoader loader) throws Exception {
    String type = request.getParameter("type");
    String name = "com.example.plugins." + type;
    return loader.loadClass(name);
  }
}
