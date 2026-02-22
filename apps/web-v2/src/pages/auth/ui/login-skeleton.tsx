import { Skeleton } from "@/shared/ui/skeleton";
import * as styles from "./login-skeleton.css";

export function LoginSkeleton() {
  return (
    <div>
      <div class={styles.fieldGroup}>
        <div class={styles.fieldItem}>
          <Skeleton variant="text" width="50px" />
          <Skeleton variant="block" height="3rem" />
        </div>
        <div class={styles.fieldItem}>
          <Skeleton variant="text" width="72px" />
          <Skeleton variant="block" height="3rem" />
        </div>
      </div>
      <Skeleton variant="text" width="130px" height="0.8125rem" />
      <Skeleton variant="block" height="3rem" />
    </div>
  );
}
