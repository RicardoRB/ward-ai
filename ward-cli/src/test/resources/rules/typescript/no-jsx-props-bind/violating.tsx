const a = <div onClick={() => go()} />;
const b = <div onClick={function () { go(); }} />;
const c = <div onClick={go.bind(this)} />;
const d = <div onClick={x => go(x)} />;
const e = <div onClick={(a, b) => go(a)} />;
