import {
  fontSizes,
  fontWeights,
  radii,
  shake,
  space,
  transitions,
  vars,
  withAlpha,
} from "../../../shared/styles/theme.css";
import { style } from "@vanilla-extract/css";

export const fieldGroup = style({
  display: "flex",
  flexDirection: "column",
  gap: space[5],
});

export const errorBox = style({
  marginBottom: space[5],
  borderRadius: radii.md,
  border: `1px solid ${withAlpha(vars.color.danger, 20)}`,
  backgroundColor: withAlpha(vars.color.danger, 10),
  padding: `${space[3]} ${space[4]}`,
  fontSize: fontSizes.sm,
  color: vars.color.danger,
  animation: `${shake} 400ms ease-in-out`,
});

export const forgotLink = style({
  marginTop: space[2],
  marginBottom: space[6],
  fontSize: "0.8125rem",
  fontWeight: fontWeights.medium,
  color: vars.color.textLink,
  textUnderlineOffset: "4px",
  transition: `color ${transitions.base}`,
  background: "none",
  border: "none",
  cursor: "pointer",
  padding: 0,
  ":hover": {
    textDecoration: "underline",
  },
});

export const submitButton = style({
  width: "100%",
  marginTop: space[6],
});
