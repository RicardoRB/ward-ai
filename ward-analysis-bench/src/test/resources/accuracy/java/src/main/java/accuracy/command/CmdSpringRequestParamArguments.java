package accuracy.command;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

class CmdSpringRequestParamArguments {
  void run(@RequestParam String target) throws Exception {
    new ProcessBuilder("sh", "-c", "ping " + target).start();
  }
}
