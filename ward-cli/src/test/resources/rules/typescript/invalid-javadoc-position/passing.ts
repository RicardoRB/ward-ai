/** Shapes. */
export class Shape {}

/** Computes the area. */
export function area(): number {
  return 1;
}

export type Callable = {
  /**
  Documents the call signature below.
  */
  <T>(schema?: undefined): Promise<T>;
};

/**
Documents the class below, past a line comment.
*/
// a line comment between the documentation and the declaration
export class Documented {}

export const handlers = {
  /**
  Documents the property below.
  */
  serial: () => 1,
};
