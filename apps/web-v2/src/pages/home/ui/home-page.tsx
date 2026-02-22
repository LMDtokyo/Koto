import { authStore } from "@/entities/user/model/auth-store";
import { useSolidStore } from "@/shared/lib/use-solid-store";
import { Button } from "@/shared/ui/button";
import { useNavigate } from "@solidjs/router";
import * as styles from "./home.css";

export function HomePage() {
  const user = useSolidStore(authStore, (s) => s.user);
  const navigate = useNavigate();

  const handleLogout = () => {
    authStore.getState().logout();
    navigate("/login");
  };

  return (
    <div class={styles.page}>
      <div class={styles.content}>
        <h1 class={styles.title}>Welcome{user() ? `, ${user()?.username}` : ""}!</h1>
        <p class={styles.subtitle}>This is the app shell. Channels coming soon.</p>
        <Button variant="secondary" onClick={handleLogout}>
          Log out
        </Button>
      </div>
    </div>
  );
}
