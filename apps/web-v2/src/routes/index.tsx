import { Navigate } from "@solidjs/router";

export default function IndexRoute() {
  return <Navigate href="/channels/@me" />;
}
