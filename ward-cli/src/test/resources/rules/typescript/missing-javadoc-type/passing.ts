/**
 * Documented class.
 */
export class Documented {}

/** Local documented. */
class Local {}

class Hidden {}

/** Alias docs. */
export type Alias = string;

/**
Documents the class below.
*/
// a line comment between the documentation and the class
export class Failure<T = unknown> extends Error {
  override name = 'Failure';
  detail?: T;
}
