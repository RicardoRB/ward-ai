import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Headers {
  void constant(HttpServletResponse response) {
    response.setHeader("X-Origin", "internal");
  }

  void numeric(HttpServletRequest request, HttpServletResponse response) {
    int version = Integer.parseInt(request.getParameter("version"));
    response.setHeader("X-Version", "v" + version);
  }

  // A number cannot carry a line break, whatever the String it is computed from.
  void count(HttpServletRequest request, HttpServletResponse response) {
    String key = request.getHeader("X-Key");
    long seconds = Math.max(1, key.length());
    response.setHeader("Retry-After", String.valueOf(seconds));
  }

  // The header value is the decimal length of the bytes; the bytes reach only the body.
  void download(HttpServletRequest request, HttpServletResponse response)
      throws java.io.IOException {
    byte[] data = request.getParameter("name").getBytes();
    length(response, data);
  }

  void length(HttpServletResponse response, byte[] data) throws java.io.IOException {
    response.setHeader("Content-Disposition", "attachment; filename=\"app.zip\"");
    response.addHeader("Content-Length", "" + data.length);
    response.getOutputStream().write(data);
  }

  void lengthLocal(HttpServletRequest request, HttpServletResponse response) {
    byte[] data = request.getParameter("name").getBytes();
    int size = data.length;
    response.setHeader("Content-Length", String.valueOf(size));
  }

  void lengthOfCopy(HttpServletResponse response, byte[] data) {
    long size = Math.max(1, data.length);
    response.setHeader("Content-Length", Long.toString(size));
  }

  void collectionSize(HttpServletRequest request, HttpServletResponse response) {
    java.util.List<String> names = java.util.Arrays.asList(request.getParameterValues("n"));
    response.setHeader("X-Count", "" + names.size());
  }
}
