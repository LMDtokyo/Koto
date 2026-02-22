import { SkeletonWrapper } from "@/shared/ui/skeleton";
import { Suspense, lazy } from "solid-js";
import { AuthLayout } from "./auth-layout";
import { LoginSkeleton } from "./login-skeleton";

const LoginForm = lazy(() =>
  import("@/features/auth/ui/login-form").then((m) => ({ default: m.LoginForm })),
);

export function LoginPage() {
  return (
    <AuthLayout
      title="Welcome back!"
      subtitle="We're so excited to see you again!"
      footerText="Need an account?"
      footerLinkText="Register"
      footerLinkTo="/register"
    >
      <Suspense fallback={<LoginSkeleton />}>
        <SkeletonWrapper isLoading={false} skeleton={<LoginSkeleton />}>
          <LoginForm />
        </SkeletonWrapper>
      </Suspense>
    </AuthLayout>
  );
}
