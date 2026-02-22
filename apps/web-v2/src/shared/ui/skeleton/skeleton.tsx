import * as styles from "./skeleton.css";

interface SkeletonProps {
  variant?: "text" | "heading" | "circle" | "block";
  width?: string;
  height?: string;
  class?: string;
}

export function Skeleton(props: SkeletonProps) {
  const variantMap = {
    text: styles.text,
    heading: styles.heading,
    circle: styles.circle,
    block: styles.block,
  };

  return (
    <div
      class={`${variantMap[props.variant ?? "text"]}${props.class ? ` ${props.class}` : ""}`}
      style={{ width: props.width, height: props.height }}
    />
  );
}
