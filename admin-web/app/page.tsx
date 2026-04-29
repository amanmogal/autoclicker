import { ApprovalActionButton } from "@/components/approval-action-button";
import { SignOutButton } from "@/components/sign-out-button";
import { requireAdminUserForPage } from "@/lib/admin/access";
import { listManagedUsers, type ManagedAuthUser, type ManagedUserStatus } from "@/lib/admin/users";

export const dynamic = "force-dynamic";

function formatDateTime(value: string | null): string {
  return value ? new Date(value).toLocaleString() : "Not recorded";
}

function actionConfigForStatus(status: ManagedUserStatus): {
  action: "approve" | "reject";
  label: string;
} {
  switch (status) {
    case "active":
      return { action: "reject", label: "Revoke Access" };
    case "pending":
    case "expired":
    case "revoked":
      return { action: "approve", label: "Approve / Renew 30 Days" };
  }
}

function UserCard({ user }: { user: ManagedAuthUser }) {
  const actionConfig = actionConfigForStatus(user.currentStatus);

  return (
    <article className="user-card">
      <div className="user-topline">
        <div className="user-email">{user.email}</div>
        <span className={`status-pill ${user.currentStatus}`}>{user.currentStatus}</span>
      </div>

      <div className="user-meta">
        <span>Created {new Date(user.createdAt).toLocaleString()}</span>
        <span>Email confirmed {formatDateTime(user.emailConfirmedAt)}</span>
        <span>Last sign-in {formatDateTime(user.lastSignInAt)}</span>
        <span>Renewed at {formatDateTime(user.lastAdminActionAt)}</span>
        <span>Expires at {formatDateTime(user.subscriptionExpiresAt)}</span>
        <span className="meta-code">uid {user.id}</span>
      </div>

      <div className="actions-row">
        <ApprovalActionButton
          action={actionConfig.action}
          label={actionConfig.label}
          userId={user.id}
        />
      </div>
    </article>
  );
}

function UserSection({
  description,
  title,
  users
}: {
  description: string;
  title: string;
  users: ManagedAuthUser[];
}) {
  return (
    <section className="surface-card">
      <div className="section-header">
        <div>
          <h2 className="section-title">{title}</h2>
          <p className="section-subtitle">{description}</p>
        </div>
      </div>

      {users.length === 0 ? (
        <div className="empty-state">Nothing to review in this lane right now.</div>
      ) : (
        <div className="user-grid">
          {users.map((user) => (
            <UserCard key={user.id} user={user} />
          ))}
        </div>
      )}
    </section>
  );
}

export default async function DashboardPage() {
  const adminUser = await requireAdminUserForPage();
  const [pendingUsers, activeUsers, expiredUsers, revokedUsers] = await Promise.all([
    listManagedUsers("pending"),
    listManagedUsers("active"),
    listManagedUsers("expired"),
    listManagedUsers("revoked")
  ]);

  return (
    <main className="shell">
      <section className="hero">
        <div className="hero-card">
          <span className="eyebrow">Bhada approval admin</span>
          <h1 className="hero-title">Approve, renew, and lock manual 30-day access.</h1>
          <p className="hero-copy">
            This panel shows confirmed Supabase Auth accounts for your non-technical admin and lets
            them start or renew a rolling 30-day access window without using the Supabase dashboard.
          </p>

          <div className="top-actions">
            <div className="muted-copy">
              Signed in as <strong>{adminUser.email}</strong>
            </div>
            <SignOutButton />
          </div>

          <div className="summary-grid">
            <div className="summary-card">
              <div className="summary-label">Pending</div>
              <div className="summary-value">{pendingUsers.length}</div>
            </div>
            <div className="summary-card">
              <div className="summary-label">Active</div>
              <div className="summary-value">{activeUsers.length}</div>
            </div>
            <div className="summary-card">
              <div className="summary-label">Expired</div>
              <div className="summary-value">{expiredUsers.length}</div>
            </div>
            <div className="summary-card">
              <div className="summary-label">Revoked</div>
              <div className="summary-value">{revokedUsers.length}</div>
            </div>
          </div>
        </div>

        <div className="hero-side">
          <section className="surface-card">
            <span className="eyebrow">Guardrails</span>
            <ul className="hero-list">
              <li>Only confirmed, non-admin emails appear here.</li>
              <li>Approve / Renew always writes a fresh 30-day expiry into Supabase auth metadata.</li>
              <li>Revoking access blocks the app immediately on the next access check.</li>
            </ul>
          </section>
        </div>
      </section>

      <div className="user-sections">
        <UserSection
          description="Confirmed accounts that still need first-time approval and manual renewal."
          title="Pending review"
          users={pendingUsers}
        />
        <UserSection
          description="Accounts currently inside an active 30-day manual access window."
          title="Active access"
          users={activeUsers}
        />
        <UserSection
          description="Accounts whose last 30-day window has expired and now need renewal."
          title="Expired access"
          users={expiredUsers}
        />
        <UserSection
          description="Accounts manually revoked by the admin and blocked until re-approved."
          title="Revoked access"
          users={revokedUsers}
        />
      </div>
    </main>
  );
}
