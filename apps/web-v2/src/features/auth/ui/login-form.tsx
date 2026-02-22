import { ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { MailIcon } from "@/shared/ui/icons";
import { Input } from "@/shared/ui/input";
import { PasswordInput } from "@/shared/ui/input";
import { createForm, zodForm } from "@modular-forms/solid";
import { Show, createSignal } from "solid-js";
import { z } from "zod";
import { useLoginMutation } from "../model/use-auth-mutations";
import * as styles from "./auth-forms.css";

const schema = z.object({
  email: z.string().email("Invalid email address"),
  password: z.string().min(1, "Password is required"),
});

type LoginFormData = z.infer<typeof schema>;

export function LoginForm() {
  const mutation = useLoginMutation();
  const [serverError, setServerError] = createSignal<string | null>(null);

  const [, { Form, Field }] = createForm<LoginFormData>({
    validate: zodForm(schema),
  });

  const onSubmit = (data: LoginFormData) => {
    setServerError(null);
    mutation.mutate(data, {
      onError: (err) => {
        setServerError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
      },
    });
  };

  return (
    <>
      <Show when={serverError()}>
        <div class={styles.errorBox}>{serverError()}</div>
      </Show>

      <Form onSubmit={onSubmit}>
        <div class={styles.fieldGroup}>
          <Field name="email">
            {(field, props) => (
              <Input
                {...props}
                label="Email"
                type="email"
                autocomplete="email"
                iconLeft={<MailIcon />}
                error={field.error}
                value={field.value ?? ""}
              />
            )}
          </Field>
          <Field name="password">
            {(field, props) => (
              <PasswordInput
                {...props}
                label="Password"
                autocomplete="current-password"
                error={field.error}
                value={field.value ?? ""}
              />
            )}
          </Field>
        </div>

        <button type="button" class={styles.forgotLink}>
          Forgot your password?
        </button>

        <Button type="submit" size="lg" loading={mutation.isPending} class={styles.submitButton}>
          Log In
        </Button>
      </Form>
    </>
  );
}
