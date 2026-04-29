import { redirect } from "next/navigation";
import { LoginForm } from "@/components/login-form";
import { getCurrentAuthenticatedUser } from "@/lib/admin/access";
import { userIsAdmin } from "@/lib/admin/users";

export const dynamic = "force-dynamic";

export default async function LoginPage() {
  const user = await getCurrentAuthenticatedUser();
  if (user && userIsAdmin(user)) {
    redirect("/");
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="eyebrow">Supabase manual renewal control</span>
        <h1 className="login-title">Admin access for approvals and 30-day renewals.</h1>
        <p className="muted-copy">
          Sign in with a Supabase account whose <code className="meta-code">app_metadata.role</code>
          {" "}
          is
          {" "}
          <code className="meta-code">admin</code>. Only the server can see or use the
          service-role key, and this panel is where the non-technical admin manages manual access.
        </p>
        <LoginForm />
      </section>
    </main>
  );
}
