import { radii, skeletonShimmer, vars } from "../../styles/theme.css";
import { style } from "@vanilla-extract/css";

export const base = style({
  borderRadius: radii.md,
  background: `linear-gradient(90deg, ${vars.color.surfaceRaised} 0%, ${vars.color.surfaceOverlay} 50%, ${vars.color.surfaceRaised} 100%)`,
  backgroundSize: "800px 100%",
  animation: `${skeletonShimmer} 2s cubic-bezier(0.4, 0, 0.6, 1) infinite`,
});

export const text = style([base, { height: "0.875rem", borderRadius: radii.sm }]);

export const heading = style([base, { height: "1.5rem", borderRadius: radii.sm }]);

export const circle = style([base, { borderRadius: radii.full }]);

export const block = style([base, { width: "100%" }]);
