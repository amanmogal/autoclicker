import type { User } from "@supabase/supabase-js";
import { createSupabaseAdminClient } from "@/lib/supabase/admin";

const THIRTY_DAY_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

export type ManagedUserStatus = "pending" | "active" | "expired" | "revoked";

export interface ManagedAuthUser {
  id: string;
  email: string;
  createdAt: string;
  lastSignInAt: string | null;
  role?: string;
  currentStatus: ManagedUserStatus;
  approved: boolean;
  approvalStatus: string;
  subscriptionStatus: string;
  emailConfirmedAt: string | null;
  subscriptionStartedAt: string | null;
  subscriptionExpiresAt: string | null;
  lastAdminActionAt: string | null;
}

function normalizeRole(user: User): string | undefined {
  const rawRole = user.app_metadata?.role;
  if (typeof rawRole !== "string") {
    return undefined;
  }
  const normalized = rawRole.trim().toLowerCase();
  return normalized || undefined;
}

function readMetadataString(user: User, key: string): string | null {
  const appValue = user.app_metadata?.[key];
  if (typeof appValue === "string" && appValue.trim()) {
    return appValue.trim();
  }

  const userValue = user.user_metadata?.[key];
  if (typeof userValue === "string" && userValue.trim()) {
    return userValue.trim();
  }

  return null;
}

function readMetadataBoolean(user: User, key: string): boolean | null {
  const rawValues = [user.app_metadata?.[key], user.user_metadata?.[key]];
  for (const raw of rawValues) {
    if (typeof raw === "boolean") {
      return raw;
    }
    if (typeof raw === "number") {
      return raw !== 0;
    }
    if (typeof raw === "string") {
      const normalized = raw.trim().toLowerCase();
      if (["true", "1", "yes", "approved", "active"].includes(normalized)) {
        return true;
      }
      if (["false", "0", "no", "pending", "rejected", "inactive", "revoked"].includes(normalized)) {
        return false;
      }
    }
  }
  return null;
}

function parseIsoDate(rawValue: string | null): number | null {
  if (!rawValue) {
    return null;
  }
  const millis = Date.parse(rawValue);
  return Number.isFinite(millis) ? millis : null;
}

function classifyManagedStatus(user: User): ManagedUserStatus {
  const approved = readMetadataBoolean(user, "approved") ?? false;
  const approvalStatus = (readMetadataString(user, "approval_status") ?? "pending").toLowerCase();
  const subscriptionStatus = (readMetadataString(user, "subscription_status") ?? "pending").toLowerCase();
  const expiresAtMs = parseIsoDate(readMetadataString(user, "subscription_expires_at"));
  const nowMs = Date.now();

  if (approvalStatus === "rejected" || approvalStatus === "revoked" || subscriptionStatus === "revoked") {
    return "revoked";
  }
  if (!approved || approvalStatus === "pending") {
    return "pending";
  }
  if (expiresAtMs !== null && expiresAtMs <= nowMs) {
    return "expired";
  }
  return "active";
}

function mapManagedUser(user: User): ManagedAuthUser {
  const approved = readMetadataBoolean(user, "approved") ?? false;
  const approvalStatus = readMetadataString(user, "approval_status") ?? "pending";
  const subscriptionStatus = readMetadataString(user, "subscription_status") ?? "pending";

  return {
    id: user.id,
    email: user.email ?? "(no email)",
    createdAt: user.created_at,
    lastSignInAt: user.last_sign_in_at ?? null,
    role: normalizeRole(user),
    currentStatus: classifyManagedStatus(user),
    approved,
    approvalStatus,
    subscriptionStatus,
    emailConfirmedAt: user.email_confirmed_at ?? user.confirmed_at ?? null,
    subscriptionStartedAt: readMetadataString(user, "subscription_started_at"),
    subscriptionExpiresAt: readMetadataString(user, "subscription_expires_at"),
    lastAdminActionAt: readMetadataString(user, "last_admin_action_at")
  };
}

async function listAllAuthUsers(): Promise<User[]> {
  const supabaseAdmin = createSupabaseAdminClient();
  const perPage = 200;
  const users: User[] = [];
  let page = 1;

  while (true) {
    const { data, error } = await supabaseAdmin.auth.admin.listUsers({ page, perPage });
    if (error) {
      throw new Error(`Unable to list auth users: ${error.message}`);
    }

    const batch = data.users ?? [];
    users.push(...batch);

    if (batch.length < perPage) {
      break;
    }
    page += 1;
  }

  return users;
}

export function userIsAdmin(user: User | null | undefined): boolean {
  if (!user) {
    return false;
  }
  return normalizeRole(user) === "admin";
}

export async function listManagedUsers(status?: ManagedUserStatus): Promise<ManagedAuthUser[]> {
  const users = await listAllAuthUsers();
  return users
    .filter((user) => !userIsAdmin(user))
    .filter((user) => Boolean(user.email_confirmed_at ?? user.confirmed_at))
    .map(mapManagedUser)
    .filter((user) => (status ? user.currentStatus === status : true))
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}

export async function updateUserApproval(
  userId: string,
  nextStatus: "approve" | "reject"
): Promise<ManagedAuthUser> {
  const supabaseAdmin = createSupabaseAdminClient();
  const { data: existingData, error: fetchError } = await supabaseAdmin.auth.admin.getUserById(userId);

  if (fetchError || !existingData.user) {
    throw new Error(fetchError?.message ?? "User not found.");
  }

  const existingUser = existingData.user;
  const now = new Date();
  const startedAt = now.toISOString();
  const expiresAt = new Date(now.getTime() + THIRTY_DAY_WINDOW_MS).toISOString();

  const mergedAppMetadata =
    nextStatus === "approve"
      ? {
          ...(existingUser.app_metadata ?? {}),
          approved: true,
          approval_status: "approved",
          subscription_status: "active",
          subscription_started_at: startedAt,
          subscription_expires_at: expiresAt,
          last_admin_action_at: startedAt
        }
      : {
          ...(existingUser.app_metadata ?? {}),
          approved: false,
          approval_status: "rejected",
          subscription_status: "revoked",
          last_admin_action_at: startedAt
        };

  const { data: updatedData, error: updateError } = await supabaseAdmin.auth.admin.updateUserById(
    userId,
    {
      app_metadata: mergedAppMetadata
    }
  );

  if (updateError || !updatedData.user) {
    throw new Error(updateError?.message ?? "Approval update failed.");
  }

  return mapManagedUser(updatedData.user);
}
