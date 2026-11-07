for (const item of items) {
  if (skip(item)) {
    continue;
  }
  handle(item);
}
