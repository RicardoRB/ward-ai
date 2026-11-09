export function greet(name: string, count: number): string {
  const greeting = 'Hello ' + name + '!';
  const nested = 'Count ' + (count + 1);
  return greeting + nested;
}
