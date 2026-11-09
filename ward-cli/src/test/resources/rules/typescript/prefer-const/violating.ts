export function total(values: number[]): number {
  let sum = 0;
  let count;
  count = values.length;
  let late;
  if (values.length) {
    late = 1;
  }
  let twice;
  twice = 1;
  twice = 2;
  let inLoop;
  for (const v of values) {
    inLoop = v;
  }
  let nested;
  const f = () => {
    nested = 1;
  };
  let early;
  console.log(early);
  early = 1;
  return sum + count + (late ?? 0) + twice + (inLoop ?? 0) + (nested ?? 0) + early + f();
}
