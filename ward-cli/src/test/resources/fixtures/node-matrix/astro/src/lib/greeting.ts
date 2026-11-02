export function greeting(name: string): string {
  if (name.trim() === "") {
    return "Hello, stranger";
  }
  return `Hello, ${name.trim()}`;
}
