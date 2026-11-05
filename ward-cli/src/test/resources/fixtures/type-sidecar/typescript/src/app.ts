interface Config {
  port: number;
  name: string;
}

const config: Config = { port: 8080, name: "x" };
const port: number = config.port;
const missing = nope.value;
export { port, missing };
