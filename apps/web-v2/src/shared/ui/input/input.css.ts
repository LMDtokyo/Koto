import { fontSizes, fontWeights, radii, space, transitions, vars } from "../../styles/theme.css";
import { style } from "@vanilla-extract/css";

export const wrapper = style({
  display: "flex",
  flexDirection: "column",
  gap: space[2],
});

export const label = style({
  fontSize: fontSizes.xs,
  fontWeight: fontWeights.bold,
  textTransform: "uppercase",
  letterSpacing: "0.06em",
  lineHeight: "1",
  color: vars.color.textSecondary,
});

export const labelError = style({
  color: vars.color.danger,
});

export const errorHint = style({
  marginLeft: space[1],
  fontWeight: fontWeights.normal,
  textTransform: "none",
  fontStyle: "italic",
  color: vars.color.danger,
});

export const fieldWrapper = style({
  position: "relative",
  isolation: "isolate",
});

export const iconSlot = style({
  position: "absolute",
  zIndex: 10,
  top: "50%",
  transform: "translateY(-50%)",
  pointerEvents: "none",
  color: vars.color.textMuted,
});

export const iconLeft = style([iconSlot, { left: space[4] }]);
export const iconRight = style([iconSlot, { right: space[4] }]);

export const input = style({
  height: "3rem",
  width: "100%",
  borderRadius: radii.md,
  backgroundColor: vars.color.inputBg,
  fontSize: fontSizes.base,
  color: vars.color.textPrimary,
  border: `1px solid ${vars.color.inputBorder}`,
  transition: `all ${transitions.base}`,
  outline: "none",
  "::placeholder": {
    color: vars.color.textMuted,
  },
  ":hover": {
    borderColor: vars.color.borderStrong,
  },
  ":focus": {
    borderColor: vars.color.accent,
    boxShadow: `0 0 0 1px color-mix(in srgb, ${vars.color.accent} 40%, transparent)`,
  },
});

export const inputError = style({
  borderColor: vars.color.danger,
});

export const inputWithIconLeft = style({
  paddingLeft: space[12],
});

export const inputWithIconRight = style({
  paddingRight: space[12],
});

export const inputDefault = style({
  paddingLeft: space[4],
  paddingRight: space[4],
});

export const toggleButton = style({
  position: "absolute",
  zIndex: 10,
  right: space[2],
  top: "50%",
  transform: "translateY(-50%)",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  width: "2.5rem",
  height: "2.5rem",
  borderRadius: radii.sm,
  color: vars.color.textMuted,
  transition: `color ${transitions.fast}`,
  background: "none",
  border: "none",
  cursor: "pointer",
  ":hover": {
    color: vars.color.textSecondary,
    backgroundColor: vars.color.bgModifierHover,
  },
  ":active": {
    backgroundColor: vars.color.bgModifierActive,
  },
});
