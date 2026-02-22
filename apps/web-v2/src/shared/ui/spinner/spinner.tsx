import { sizes } from "./spinner.css";

interface SpinnerProps {
  size?: "sm" | "md" | "lg";
}

export function Spinner(props: SpinnerProps) {
  return <div class={sizes[props.size ?? "md"]} role="status" aria-label="Loading" />;
}
