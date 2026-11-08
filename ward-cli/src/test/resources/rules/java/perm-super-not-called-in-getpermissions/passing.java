class Loader extends java.net.URLClassLoader {
  Loader() {
    super(null);
  }

  protected java.security.PermissionCollection getPermissions(java.security.CodeSource source) {
    return super.getPermissions(source);
  }
}

class Plain {
  java.security.PermissionCollection getPermissions() {
    return null;
  }
}

