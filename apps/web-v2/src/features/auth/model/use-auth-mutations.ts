import { authStore } from "@/entities/user/model/auth-store";
import { useNavigate } from "@solidjs/router";
import { createMutation } from "@tanstack/solid-query";
import * as authApi from "../api/auth-api";

export function useLoginMutation() {
  const navigate = useNavigate();

  return createMutation(() => ({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      authStore.getState().setAuth(data.user, data.access_token);
      navigate("/channels/@me");
    },
  }));
}

export function useRegisterMutation() {
  const navigate = useNavigate();

  return createMutation(() => ({
    mutationFn: authApi.register,
    onSuccess: (data) => {
      authStore.getState().setAuth(data.user, data.access_token);
      navigate("/channels/@me");
    },
  }));
}
