import { readFile } from "node:fs";

export function load(path) {
  return readFile(path, "utf8");
}
