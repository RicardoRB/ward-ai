import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPath;

class Lookup {
  Object constant(XPath xpath, Object document) throws Exception {
    return xpath.evaluate("//user[@name='admin']", document);
  }

  Object numeric(HttpServletRequest request, XPath xpath, Object document) throws Exception {
    int index = Integer.parseInt(request.getParameter("index"));
    return xpath.evaluate("//user[" + index + "]", document);
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request, XPath xpath, Object document) throws Exception {
    String expression = read(request);
    return xpath.evaluate(expression, document);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("query");
  }
}
