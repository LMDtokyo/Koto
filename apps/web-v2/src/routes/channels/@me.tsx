import { authStore } from "@/entities/user/model/auth-store";
import { HomePage } from "@/pages/home";
import { Navigate } from "@solidjs/router";

export default function ChannelsMeRoute() {
  if (!authStore.getState().isAuthenticated) {
    return <Navigate href="/login" />;
  }
  return <HomePage />;
}
