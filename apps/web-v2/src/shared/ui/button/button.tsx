import { Spinner } from "@/shared/ui/spinner";
import { Show, splitProps } from "solid-js";
import type { JSX } from "solid-js";
import { button } from "./button.css";

type Variant = "primary" | "secondary" | "danger" | "ghost";
type Size = "sm" | "md" | "lg";

interface ButtonProps extends JSX.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

export function Button(props: ButtonProps) {
  const [local, rest] = splitProps(props, [
    "variant",
    "size",
    "loading",
    "children",
    "class",
    "disabled",
  ]);

  return (
    <button
      {...rest}
      disabled={local.disabled || local.loading}
      class={`${button({ variant: local.variant ?? "primary", size: local.size ?? "md" })}${local.class ? ` ${local.class}` : ""}`}
    >
      <Show when={local.loading} fallback={local.children}>
        <Spinner size="sm" />
        <span>{local.children}</span>
      </Show>
    </button>
  );
}
