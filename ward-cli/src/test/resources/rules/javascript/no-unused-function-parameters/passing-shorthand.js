function make(id, total) {
  return { id, total };
}
const wrap = (id) => ({ id });
function swap(a, b) {
  return { b, a };
}
