export function Banner(props) {
  return { dangerouslySetInnerHTML: { __html: props.html } };
}
