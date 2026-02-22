import { SkeletonWrapper } from "@/shared/ui/skeleton";
import { Suspense, lazy } from "solid-js";
import { AuthLayout } from "./auth-layout";
import { RegisterSkeleton } from "./register-skeleton";

const RegisterForm = lazy(() =>
  import("@/features/auth/ui/register-form").then((m) => ({ default: m.RegisterForm })),
);

export function RegisterPage() {
  return (
    <AuthLayout
      title="Create an account"
      subtitle="Join the conversation today"
      footerText="Already have an account?"
      footerLinkText="Log In"
      footerLinkTo="/login"
    >
      <Suspense fallback={<RegisterSkeleton />}>
        <SkeletonWrapper isLoading={false} skeleton={<RegisterSkeleton />}>
          <RegisterForm />
        </SkeletonWrapper>
      </Suspense>
    </AuthLayout>
  );
}
