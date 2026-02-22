import { fontSizes, fontWeights, space, transitions, vars } from "../../../shared/styles/theme.css";
import { style } from "@vanilla-extract/css";

export const page = style({
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  minHeight: "100vh",
  backgroundColor: vars.color.bgPrimary,
  gap: space[4],
});

export const code = style({
  fontSize: "6rem",
  fontWeight: fontWeights.bold,
  color: vars.color.textMuted,
  lineHeight: "1",
});

export const message = style({
  fontSize: fontSizes.lg,
  color: vars.color.textSecondary,
});

export const link = style({
  color: vars.color.textLink,
  fontWeight: fontWeights.medium,
  textDecoration: "none",
  transition: `color ${transitions.fast}`,
  ":hover": {
    textDecoration: "underline",
  },
});
