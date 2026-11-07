final class Naming {
  Object find(javax.naming.Context context) throws Exception {
    return context.lookup("name");
  }
}
