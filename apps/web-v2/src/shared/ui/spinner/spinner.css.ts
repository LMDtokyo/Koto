import { spin, vars } from "../../styles/theme.css";
import { style, styleVariants } from "@vanilla-extract/css";

const base = style({
  borderRadius: "9999px",
  borderWidth: "2px",
  borderStyle: "solid",
  borderColor: vars.color.accent,
  borderRightColor: "transparent",
  animation: `${spin} 0.6s linear infinite`,
});

export const sizes = styleVariants({
  sm: [base, { width: "1rem", height: "1rem" }],
  md: [base, { width: "1.5rem", height: "1.5rem" }],
  lg: [base, { width: "2.5rem", height: "2.5rem" }],
});
