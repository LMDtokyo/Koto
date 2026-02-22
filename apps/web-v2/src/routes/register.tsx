import { authStore } from "@/entities/user/model/auth-store";
import { RegisterPage } from "@/pages/auth";
import { Navigate } from "@solidjs/router";

export default function RegisterRoute() {
  if (authStore.getState().isAuthenticated) {
    return <Navigate href="/channels/@me" />;
  }
  return <RegisterPage />;
}
