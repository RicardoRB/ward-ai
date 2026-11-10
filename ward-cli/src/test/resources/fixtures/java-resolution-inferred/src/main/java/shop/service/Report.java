package shop.service;

import shop.domain.Repository;

public final class Report {
  public int total(final Repository repository) {
    var order = repository.find("a");
    final int chained = repository.find("bb").total();
    final int summed = repository.all().stream().mapToInt(item -> item.total()).sum();
    return order.total() + chained + summed;
  }
}
