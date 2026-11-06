import { readFile } from "node:fs";

export function load(path: string): Promise<string> {
  return readFile(path, "utf8");
}
