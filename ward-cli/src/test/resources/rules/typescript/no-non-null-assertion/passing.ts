export function first(items: string[]): string | undefined {
  const value = items[0];
  return value?.trim();
}
