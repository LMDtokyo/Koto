import { A } from "@solidjs/router";
import * as styles from "./not-found.css";

export function NotFoundPage() {
  return (
    <div class={styles.page}>
      <div class={styles.code}>404</div>
      <p class={styles.message}>This page could not be found.</p>
      <A href="/" class={styles.link}>
        Go back home
      </A>
    </div>
  );
}
