import { authStore } from "@/entities/user/model/auth-store";
import { LoginPage } from "@/pages/auth";
import { Navigate } from "@solidjs/router";

export default function LoginRoute() {
  if (authStore.getState().isAuthenticated) {
    return <Navigate href="/channels/@me" />;
  }
  return <LoginPage />;
}
