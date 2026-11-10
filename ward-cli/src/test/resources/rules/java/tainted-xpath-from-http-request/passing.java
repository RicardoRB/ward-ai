import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response, XPath xpath, Object document) throws Exception {
    String value = request.getParameter("v");
    xpath.evaluate("//u", document);
  }
}
