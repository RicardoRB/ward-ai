class Check {
  isFailure(err: unknown): err is Error {
    return err instanceof Error;
  }
  isKnown(
    value: Item,
  ): value is
    First | Second {
    return true;
  }
  isEither(value: unknown): value is First | Second {
    return true;
  }
}
function isText(value: unknown): value is string | symbol {
  return typeof value === 'string';
}
class Guard {
  isModule(module: unknown): module is Meta {
    return true;
  }
  asserts(value: unknown): asserts value is string {}
  isSelf(): this is Guard {
    return true;
  }
  optional?<T extends object>(value: T): T;
  plain?(): void;
}
