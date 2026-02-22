import { Show, createUniqueId, splitProps } from "solid-js";
import type { JSX } from "solid-js";
import * as styles from "./input.css";

interface InputProps extends JSX.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  iconLeft?: JSX.Element;
  iconRight?: JSX.Element;
  ref?: HTMLInputElement | ((el: HTMLInputElement) => void);
}

export function Input(props: InputProps) {
  const [local, rest] = splitProps(props, [
    "label",
    "error",
    "iconLeft",
    "iconRight",
    "class",
    "id",
    "ref",
  ]);

  const generatedId = createUniqueId();
  const id = () => local.id ?? generatedId;
  const errorId = () => `${id()}-error`;

  const inputClasses = () =>
    [
      styles.input,
      local.iconLeft ? styles.inputWithIconLeft : styles.inputDefault,
      local.iconRight ? styles.inputWithIconRight : "",
      local.error ? styles.inputError : "",
      local.class ?? "",
    ]
      .filter(Boolean)
      .join(" ");

  return (
    <div class={styles.wrapper}>
      <label for={id()} class={`${styles.label} ${local.error ? styles.labelError : ""}`}>
        {local.label}
        <Show when={local.error}>
          <span class={styles.errorHint}>&mdash; {local.error}</span>
        </Show>
      </label>
      <div class={styles.fieldWrapper}>
        <Show when={local.iconLeft}>
          <span class={styles.iconLeft}>{local.iconLeft}</span>
        </Show>
        <input
          ref={local.ref}
          id={id()}
          aria-invalid={!!local.error}
          aria-describedby={local.error ? errorId() : undefined}
          class={inputClasses()}
          {...rest}
        />
        <Show when={local.iconRight}>
          <span class={styles.iconRight}>{local.iconRight}</span>
        </Show>
      </div>
    </div>
  );
}
