declare global {
  interface Window {
    appVersion: string;
  }
}

declare module "lib" {
  export const value: number;
}

namespace Util {
  export const x = 1;
}
