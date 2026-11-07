final class Orders {
  private final Repository repository;

  @Inject
  Orders(final Repository repository) {
    this.repository = repository;
  }
}
