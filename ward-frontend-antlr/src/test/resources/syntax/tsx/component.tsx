import { Button } from "./button";
import * as ui from "ui-kit";

type Props = { label: string; onClick?: () => void };

export function Card({ label, onClick, ...rest }: Props) {
  const items = [1, 2, 3];
  const count = items.length < 3 ? 1 : 2;
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
      {count}
    </section>
  );
}
