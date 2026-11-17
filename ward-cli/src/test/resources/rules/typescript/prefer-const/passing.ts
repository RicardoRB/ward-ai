export function total(values: number[]): number {
  let count = values.length;
  count += 1;
  let late;
  if (values.length) {
    late = 1;
  }
  return count + (late ?? 0);
}

let stored: string | undefined;
export const store = (value: string) => {
  stored = value;
};
export const read = () => stored;

export class Failure {
  describe() {
    let assigned: string | undefined;
    const set = (value: string) => {
      assigned = value;
    };
    set('x');
    return assigned;
  }
}
