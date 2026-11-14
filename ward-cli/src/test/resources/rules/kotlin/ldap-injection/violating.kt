import javax.naming.directory.DirContext
import javax.naming.directory.SearchControls
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, context: DirContext) {
    context.search("ou=people", request.getParameter("x"), SearchControls())
  }

  fun propagated(request: HttpServletRequest, context: DirContext) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    context.search("ou=people", text, SearchControls())
  }

  fun annotated(@RequestParam value: String, context: DirContext) {
    context.search("ou=people", "[${value.trim()}]", SearchControls())
  }

  fun crossMethod(request: HttpServletRequest, context: DirContext) {
    val value = read(request)
    context.search("ou=people", value, SearchControls())
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
