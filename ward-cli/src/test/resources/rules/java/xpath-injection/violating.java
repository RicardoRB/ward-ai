import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPath;

class Lookup {
  Object direct(HttpServletRequest request, XPath xpath, Object document) throws Exception {
    return xpath.evaluate(request.getParameter("query"), document);
  }

  Object propagated(HttpServletRequest request, XPath xpath, Object document) throws Exception {
    String name = request.getParameter("name");
    String expression = "//user[@name='" + name + "']";
    return xpath.evaluate(expression, document);
  }

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, XPath xpath, Object document) throws Exception {
    String expression = read(request);
    return xpath.evaluate(expression, document);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("query");
  }
}
