String(1);
class CustomToString {
  toString() {
    return "ok";
  }
}
const pad = 0;
const c = new CustomToString();
String(c);
// keep
`${c}`;
