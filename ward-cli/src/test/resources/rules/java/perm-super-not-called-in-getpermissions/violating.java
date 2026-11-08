class Loader extends ClassLoader {
  protected java.security.PermissionCollection getPermissions(java.security.CodeSource source) {
    return new java.security.Permissions();
  }
}

