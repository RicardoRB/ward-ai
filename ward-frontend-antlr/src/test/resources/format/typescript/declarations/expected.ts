import { readFile } from "node:fs";
import type { Config } from "./config";
export const VERSION: string = "1.0";
let count = 0;
const double = (value: number): number => value * 2;
export function greet(name: string, greeting: string = "Hello"): string {
  return greeting + ", " + name;
}
export class Greeter {
  private readonly prefix: string = "> ";
  constructor(private name: string) {}
  greet(): string {
    return this.prefix + greet(this.name, "Hi");
  }
}
