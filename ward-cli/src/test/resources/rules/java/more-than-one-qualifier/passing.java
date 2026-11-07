@Qualifier
@interface Mine {}

@interface Other {}

final class T {
  @Mine @Other String a;
  @Named("a") String b;
}
