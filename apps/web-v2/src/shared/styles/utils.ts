/** color-mix helper for opacity modifiers in VE styles */
export function withAlpha(cssVar: string, percent: number): string {
  return `color-mix(in srgb, ${cssVar} ${percent}%, transparent)`;
}
