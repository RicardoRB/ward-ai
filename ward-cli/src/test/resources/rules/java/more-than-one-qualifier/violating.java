@Qualifier
@interface Mine {}

final class T {
  @Named("a") @Mine String s;
}
