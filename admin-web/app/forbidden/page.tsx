import { SignOutButton } from "@/components/sign-out-button";
import { requireLoggedInUserForPage } from "@/lib/admin/access";

export const dynamic = "force-dynamic";

export default async function ForbiddenPage() {
  const user = await requireLoggedInUserForPage();

  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="eyebrow">Access blocked</span>
        <h1 className="login-title">This account is not an approval admin.</h1>
        <p className="muted-copy">
          Signed in as <strong>{user.email}</strong>. Set
          {" "}
          <code className="meta-code">app_metadata.role=admin</code>
          {" "}
          in Supabase for the bootstrap admin, then sign in again.
        </p>
        <div className="top-actions">
          <SignOutButton />
        </div>
      </section>
    </main>
  );
}
