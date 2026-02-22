import { Show } from "solid-js";
import type { JSX } from "solid-js";
import { Motion, Presence } from "solid-motionone";

interface SkeletonWrapperProps {
  isLoading: boolean;
  skeleton: JSX.Element;
  children: JSX.Element;
}

export function SkeletonWrapper(props: SkeletonWrapperProps) {
  return (
    <Presence exitBeforeEnter>
      <Show
        when={!props.isLoading}
        fallback={
          <Motion.div exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
            {props.skeleton}
          </Motion.div>
        }
      >
        <Motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, easing: [0.16, 1, 0.3, 1] }}
        >
          {props.children}
        </Motion.div>
      </Show>
    </Presence>
  );
}
