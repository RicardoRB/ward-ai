import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

class Check {
  void run(@RequestParam String value) throws Exception {
    new RedirectView("/home");
  }
}
