class Passing {
  @AssistedInject
  Passing(@Assisted String name) {
  }
}
@Singleton
class Other {
  @Inject
  Other(String name) {
  }
}
