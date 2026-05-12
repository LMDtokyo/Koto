import type { CSSProperties } from "react";

/** CSS custom property для `.settings-row__ic`. */
export function rowIc(hex: string): CSSProperties {
  return { "--row-ic": hex } as CSSProperties;
}
