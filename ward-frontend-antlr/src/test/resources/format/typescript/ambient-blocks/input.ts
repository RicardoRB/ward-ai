declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<object, object, unknown>;
  export default component;
}

declare global {
  interface Window {
    appVersion: string;
  }
  const build: number;
}

namespace Shapes {
  export const sides = 4;
  export function area(side: number): number {
    return side * side;
  }
}

declare module "empty-shim" {
}
