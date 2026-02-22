import { space } from "../../../shared/styles/theme.css";
import { style } from "@vanilla-extract/css";

export const fieldGroup = style({
  display: "flex",
  flexDirection: "column",
  gap: space[5],
});

export const fieldItem = style({
  display: "flex",
  flexDirection: "column",
  gap: space[2],
});
