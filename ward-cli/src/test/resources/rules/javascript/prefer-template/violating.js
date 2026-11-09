export function greet(name, count) {
  const greeting = 'Hello ' + name + '!';
  const nested = 'Count ' + (count + 1);
  return greeting + nested;
}
