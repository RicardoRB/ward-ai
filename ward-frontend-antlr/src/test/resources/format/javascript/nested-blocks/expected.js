function walk(nodes) {
  for (const node of nodes) {
    if (node.children) {
      walk(node.children);
    } else if (!node.leaf) {
      node.visit();
    }
  }
  while (true) {
    break;
  }
  do {
    count++
  } while (count < 3)
  return;
}
