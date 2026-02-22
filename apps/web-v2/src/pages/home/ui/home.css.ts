import { fontSizes, fontWeights, space, vars } from "../../../shared/styles/theme.css";
import { style } from "@vanilla-extract/css";

export const page = style({
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  minHeight: "100vh",
  backgroundColor: vars.color.bgPrimary,
});

export const content = style({
  textAlign: "center",
  display: "flex",
  flexDirection: "column",
  gap: space[4],
  alignItems: "center",
});

export const title = style({
  fontSize: fontSizes["3xl"],
  fontWeight: fontWeights.bold,
  color: vars.color.textPrimary,
});

export const subtitle = style({
  fontSize: fontSizes.base,
  color: vars.color.textSecondary,
});
