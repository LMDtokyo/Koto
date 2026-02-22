import {
  breakpoints,
  cardIn,
  fadeIn,
  fontSizes,
  fontWeights,
  radii,
  space,
  transitions,
  vars,
} from "../../../shared/styles/theme.css";
import { style } from "@vanilla-extract/css";

export const page = style({
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  minHeight: "100vh",
  backgroundColor: vars.color.bgPrimary,
  position: "relative",
  overflow: "hidden",
  padding: space[4],
});

export const glowPrimary = style({
  position: "absolute",
  width: "600px",
  height: "600px",
  borderRadius: radii.full,
  background: vars.color.glowPrimary,
  filter: "blur(120px)",
  top: "-200px",
  left: "-100px",
  pointerEvents: "none",
  animation: `${fadeIn} 2s ease`,
});

export const glowSecondary = style({
  position: "absolute",
  width: "500px",
  height: "500px",
  borderRadius: radii.full,
  background: vars.color.glowSecondary,
  filter: "blur(100px)",
  bottom: "-150px",
  right: "-100px",
  pointerEvents: "none",
  animation: `${fadeIn} 2s ease`,
});

export const card = style({
  position: "relative",
  zIndex: 1,
  width: "100%",
  maxWidth: "440px",
  borderRadius: radii.xl,
  backgroundColor: vars.color.bgSecondary,
  border: `1px solid ${vars.color.border}`,
  boxShadow: vars.shadow.high,
  padding: `${space[8]} ${space[6]}`,
  animation: `${cardIn} 500ms ease forwards`,
  "@media": {
    [`(min-width: ${breakpoints.sm})`]: {
      padding: `${space[10]} ${space[8]}`,
    },
  },
});

export const header = style({
  textAlign: "center",
  marginBottom: space[8],
});

export const title = style({
  fontSize: fontSizes["2xl"],
  fontWeight: fontWeights.bold,
  color: vars.color.textPrimary,
  marginBottom: space[2],
});

export const subtitle = style({
  fontSize: fontSizes.base,
  color: vars.color.textSecondary,
});

export const footer = style({
  marginTop: space[6],
  textAlign: "center",
  fontSize: fontSizes.sm,
  color: vars.color.textMuted,
});

export const footerLink = style({
  marginLeft: space[1],
  color: vars.color.textLink,
  fontWeight: fontWeights.medium,
  textDecoration: "none",
  transition: `color ${transitions.fast}`,
  ":hover": {
    textDecoration: "underline",
  },
});
