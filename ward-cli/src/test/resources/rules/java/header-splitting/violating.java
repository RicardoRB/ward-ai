import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Headers {
  void direct(HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("X-Origin", request.getParameter("origin"));
  }

  void propagated(HttpServletRequest request, HttpServletResponse response) {
    String name = request.getParameter("name");
    String value = "user=" + name;
    response.addHeader("X-User", value);
  }

  // A value that another method of the file returns is followed.
  void crossMethod(HttpServletRequest request, HttpServletResponse response) {
    String origin = read(request);
    response.setHeader("X-Origin", origin);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("origin");
  }

  // A String keeps its line breaks.
  void text(HttpServletRequest request, HttpServletResponse response) {
    String key = request.getHeader("X-Key");
    response.setHeader("Retry-After", key.trim());
  }

  // The bytes themselves, decoded into text, are written to the header.
  void bytesAsText(HttpServletRequest request, HttpServletResponse response) {
    byte[] data = request.getParameter("name").getBytes();
    response.setHeader("X-Data", new String(data));
  }

  void textAndLength(HttpServletRequest request, HttpServletResponse response) {
    byte[] data = request.getParameter("name").getBytes();
    response.setHeader("X-Name", new String(data) + data.length);
  }

  void viaHelper(HttpServletRequest request, HttpServletResponse response) {
    byte[] data = request.getParameter("name").getBytes();
    describe(response, data);
  }

  void describe(HttpServletResponse response, byte[] data) {
    response.setHeader("X-Data", new String(data));
  }
}
