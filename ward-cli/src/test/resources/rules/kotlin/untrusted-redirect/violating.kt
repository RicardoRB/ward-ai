import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, response: HttpServletResponse) {
    response.sendRedirect(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, response: HttpServletResponse) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    response.sendRedirect(text)
  }

  fun annotated(@RequestParam value: String, response: HttpServletResponse) {
    response.sendRedirect("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, response: HttpServletResponse) {
    val value = read(request)
    response.sendRedirect(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}

class KtorRedirect {
  fun go() {
    val target = call.request.queryParameters["to"]!!
    call.respondRedirect(target)
  }

  fun scoped() {
    call.parameters["to"]?.also { call.respondRedirect(it) }
  }
}

class KtorRedirectTrimmed {
  fun next() {
    val target = call.parameters["to"]?.trim() ?: "/"
    call.respondRedirect("/r/" + target)
  }
}
