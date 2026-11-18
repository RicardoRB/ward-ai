export function total(values: number[]): number {
  const count = values.length;
  return count;
}

export async function load() {
  let Constructor;
  try {
    Constructor = (await import('./cache.js')).default;
  } catch {
    return undefined;
  }

  return new Constructor();
}

export const failure = (code: string) => {
  const error = new Error('failed');
  (error as Error & {code: string}).code = code;
  return error;
};
