import javax.naming.directory.DirContext
import javax.naming.directory.SearchControls
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, context: DirContext) {
    val unused = request.getParameter("x")
    context.search("ou=people", "fixed", SearchControls())
  }

  fun overwritten(request: HttpServletRequest, context: DirContext) {
    var value = request.getParameter("x")
    value = "fixed"
    context.search("ou=people", value, SearchControls())
  }
}
