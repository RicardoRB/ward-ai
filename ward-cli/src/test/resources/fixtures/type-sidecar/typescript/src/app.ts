interface Config {
  port: number;
  name: string;
}

const config: Config = { port: 8080, name: "x" };
const port: number = config.port;
const missing = nope.value;
abstract class Base {
  abstract run(): number;
}
class Impl extends Base {
  run(): number {
    return 1;
  }
}
const biggest = Math.max(1, 2);
export { port, missing, Impl, biggest };
