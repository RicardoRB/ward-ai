package fixture;

import javax.inject.Inject;

final class Orders {
  @Inject private Repository repository;

  @org.springframework.beans.factory.annotation.Autowired
  private Clock clock;

  void place() {}
}
