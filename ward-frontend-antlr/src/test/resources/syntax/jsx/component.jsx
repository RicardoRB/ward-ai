import { Button } from "./button";

export function Card({ label, onClick, ...rest }) {
  const items = [1, 2, 3];
  return (
    <section className="card" data-id={label} {...rest}>
      <ui.Header title="Hi" />
      <Button onClick={() => onClick()}>{label}</Button>
      <>
        <p>
          Text with an apostrophe's here & more
        </p>
      </>
      {items.map((item) => <li key={item}>{item}</li>)}
      <a href='https://example.com' aria-label={`go ${label}`} disabled />
      <svg:rect width="1" />
    </section>
  );
}
