const f = <div onClick={go} />;
const g = <div onClick={handler} />;
const h = <div onClick={wrap(x.bind(a))} />;
const i = <div>{() => go()}</div>;
const j = <div title={"x".bind}>x</div>;
