import javax.servlet.http.HttpServletRequest
import javax.xml.xpath.XPath
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, xpath: XPath, document: Any) {
    val unused = request.getParameter("x")
    xpath.evaluate("fixed", document)
  }

  fun overwritten(request: HttpServletRequest, xpath: XPath, document: Any) {
    var value = request.getParameter("x")
    value = "fixed"
    xpath.evaluate(value, document)
  }
}
