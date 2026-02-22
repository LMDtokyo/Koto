import { Router } from "@solidjs/router";
import { FileRoutes } from "@solidjs/start/router";
import { QueryClient, QueryClientProvider } from "@tanstack/solid-query";
import { Suspense } from "solid-js";
import "./shared/styles/global.css";

// Side-effect: initializes auth store, binds token accessor, restores persisted token
import "@/entities/user/model/auth-store";
// Side-effect: initializes UI store, applies persisted theme
import "@/entities/user/model/ui-store";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

export default function Root() {
  return (
    <Router
      root={(props) => (
        <QueryClientProvider client={queryClient}>
          <Suspense>{props.children}</Suspense>
        </QueryClientProvider>
      )}
    >
      <FileRoutes />
    </Router>
  );
}
