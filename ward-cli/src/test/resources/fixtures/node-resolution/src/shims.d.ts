declare module "react" {
  const React: unknown;
  export default React;
}

declare module "node:fs" {
  export function readFile(path: string): void;
}
