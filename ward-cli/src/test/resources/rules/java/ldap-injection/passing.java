import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.servlet.http.HttpServletRequest;
import org.springframework.ldap.support.LdapEncoder;

class Directory {
  Object encoded(HttpServletRequest request, DirContext context) throws Exception {
    String user = LdapEncoder.filterEncode(request.getParameter("uid"));
    return context.search("ou=people", "(uid=" + user + ")", new SearchControls());
  }

  Object constant(DirContext context) throws Exception {
    return context.search("ou=people", "(objectClass=person)", new SearchControls());
  }
}
