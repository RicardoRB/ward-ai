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

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request) throws Exception {
    String type = read(request);
    return Class.forName(type);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("type");
  }
}
