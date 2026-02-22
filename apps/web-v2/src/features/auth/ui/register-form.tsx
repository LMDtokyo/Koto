import { ApiError } from "@/shared/api/client";
import { MAX_USERNAME_LENGTH } from "@/shared/config/constants";
import { Button } from "@/shared/ui/button";
import { MailIcon, UserIcon } from "@/shared/ui/icons";
import { Input, PasswordInput } from "@/shared/ui/input";
import { createForm, zodForm } from "@modular-forms/solid";
import { Show, createSignal } from "solid-js";
import { z } from "zod";
import { useRegisterMutation } from "../model/use-auth-mutations";
import * as styles from "./auth-forms.css";

const schema = z
  .object({
    email: z.string().email("Invalid email address"),
    username: z
      .string()
      .min(2, "At least 2 characters")
      .max(MAX_USERNAME_LENGTH, `Max ${MAX_USERNAME_LENGTH} characters`)
      .regex(/^[a-zA-Z0-9_.-]+$/, "Letters, numbers, _, . and - only"),
    password: z.string().min(8, "At least 8 characters"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords don't match",
    path: ["confirmPassword"],
  });

type RegisterFormData = z.infer<typeof schema>;

export function RegisterForm() {
  const mutation = useRegisterMutation();
  const [serverError, setServerError] = createSignal<string | null>(null);

  const [, { Form, Field }] = createForm<RegisterFormData>({
    validate: zodForm(schema),
  });

  const onSubmit = (data: RegisterFormData) => {
    setServerError(null);
    mutation.mutate(
      { username: data.username, email: data.email, password: data.password },
      {
        onError: (err) => {
          setServerError(
            err instanceof ApiError ? err.message : "Something went wrong. Try again.",
          );
        },
      },
    );
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
          <Field name="username">
            {(field, props) => (
              <Input
                {...props}
                label="Username"
                autocomplete="username"
                iconLeft={<UserIcon />}
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
                autocomplete="new-password"
                error={field.error}
                value={field.value ?? ""}
              />
            )}
          </Field>
          <Field name="confirmPassword">
            {(field, props) => (
              <PasswordInput
                {...props}
                label="Confirm password"
                autocomplete="new-password"
                error={field.error}
                value={field.value ?? ""}
              />
            )}
          </Field>
        </div>

        <Button type="submit" size="lg" loading={mutation.isPending} class={styles.submitButton}>
          Continue
        </Button>
      </Form>
    </>
  );
}
