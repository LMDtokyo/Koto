import { LockIcon } from "@/shared/ui/icons";
import { Show, createSignal, createUniqueId, splitProps } from "solid-js";
import type { JSX } from "solid-js";
import * as styles from "./input.css";

interface PasswordInputProps extends Omit<JSX.InputHTMLAttributes<HTMLInputElement>, "type"> {
  label: string;
  error?: string;
  ref?: HTMLInputElement | ((el: HTMLInputElement) => void);
}

export function PasswordInput(props: PasswordInputProps) {
  const [local, rest] = splitProps(props, ["label", "error", "class", "id", "ref"]);

  const generatedId = createUniqueId();
  const id = () => local.id ?? generatedId;
  const errorId = () => `${id()}-error`;
  const [visible, setVisible] = createSignal(false);

  const inputClasses = () =>
    [
      styles.input,
      styles.inputWithIconLeft,
      styles.inputWithIconRight,
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
        <span class={styles.iconLeft}>
          <LockIcon />
        </span>
        <input
          ref={local.ref}
          id={id()}
          type={visible() ? "text" : "password"}
          aria-invalid={!!local.error}
          aria-describedby={local.error ? errorId() : undefined}
          class={inputClasses()}
          {...rest}
        />
        <button
          type="button"
          tabIndex={-1}
          onClick={() => setVisible((v) => !v)}
          aria-label={visible() ? "Hide password" : "Show password"}
          class={styles.toggleButton}
        >
          <Show when={visible()} fallback={<EyeIcon />}>
            <EyeOffIcon />
          </Show>
        </button>
      </div>
    </div>
  );
}

function EyeIcon() {
  return (
    <svg
      aria-hidden="true"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg
      aria-hidden="true"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  );
}
