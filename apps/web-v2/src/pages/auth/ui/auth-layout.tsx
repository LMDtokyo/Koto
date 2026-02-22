import { A } from "@solidjs/router";
import type { JSX } from "solid-js";
import * as styles from "./auth-layout.css";

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  footerText: string;
  footerLinkText: string;
  footerLinkTo: string;
  children: JSX.Element;
}

export function AuthLayout(props: AuthLayoutProps) {
  return (
    <div class={styles.page}>
      <div class={styles.glowPrimary} />
      <div class={styles.glowSecondary} />

      <div class={styles.card}>
        <div class={styles.header}>
          <h1 class={styles.title}>{props.title}</h1>
          <p class={styles.subtitle}>{props.subtitle}</p>
        </div>

        {props.children}

        <div class={styles.footer}>
          {props.footerText}
          <A href={props.footerLinkTo} class={styles.footerLink}>
            {props.footerLinkText}
          </A>
        </div>
      </div>
    </div>
  );
}
