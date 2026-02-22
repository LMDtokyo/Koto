import { Skeleton } from "@/shared/ui/skeleton";
import * as styles from "./login-skeleton.css";

export function RegisterSkeleton() {
  return (
    <div>
      <div class={styles.fieldGroup}>
        <div class={styles.fieldItem}>
          <Skeleton variant="text" width="45px" />
          <Skeleton variant="block" height="3rem" />
        </div>
        <div class={styles.fieldItem}>
          <Skeleton variant="text" width="72px" />
          <Skeleton variant="block" height="3rem" />
        </div>
        <div class={styles.fieldItem}>
          <Skeleton variant="text" width="72px" />
          <Skeleton variant="block" height="3rem" />
        </div>
        <div class={styles.fieldItem}>
          <Skeleton variant="text" width="120px" />
          <Skeleton variant="block" height="3rem" />
        </div>
      </div>
      <Skeleton variant="block" height="3rem" />
    </div>
  );
}
