import { fontSizes, fontWeights, radii, space, transitions, vars } from "../../styles/theme.css";
import { recipe } from "@vanilla-extract/recipes";

export const button = recipe({
  base: {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    userSelect: "none",
    fontWeight: fontWeights.semibold,
    transition: `all ${transitions.base}`,
    borderRadius: radii.md,
    border: "none",
    outline: "none",
    gap: space[2],
    ":focus-visible": {
      outline: `2px solid ${vars.color.accent}`,
      outlineOffset: "2px",
    },
    selectors: {
      "&:disabled": {
        cursor: "not-allowed",
        opacity: "0.5",
      },
    },
  },
  variants: {
    variant: {
      primary: {
        backgroundColor: vars.color.accent,
        color: vars.color.accentText,
        ":hover": { backgroundColor: vars.color.accentHover },
        ":active": { backgroundColor: vars.color.accentHover, opacity: "0.9" },
      },
      secondary: {
        backgroundColor: vars.color.surfaceRaised,
        color: vars.color.textPrimary,
        ":hover": { backgroundColor: vars.color.surfaceOverlay },
        ":active": { backgroundColor: vars.color.surfaceOverlay, opacity: "0.8" },
      },
      danger: {
        backgroundColor: vars.color.danger,
        color: vars.color.accentText,
        ":hover": { backgroundColor: vars.color.dangerHover },
        ":active": { backgroundColor: vars.color.dangerHover, opacity: "0.9" },
      },
      ghost: {
        backgroundColor: "transparent",
        color: vars.color.textSecondary,
        ":hover": { backgroundColor: vars.color.bgModifierHover },
        ":active": { backgroundColor: vars.color.bgModifierActive },
      },
    },
    size: {
      sm: { height: "2.25rem", paddingInline: space[4], fontSize: fontSizes.sm },
      md: { height: "2.5rem", paddingInline: space[5], fontSize: fontSizes.sm },
      lg: { height: "3rem", paddingInline: space[6], fontSize: "0.9375rem" },
    },
  },
  defaultVariants: {
    variant: "primary",
    size: "md",
  },
});
