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
}
