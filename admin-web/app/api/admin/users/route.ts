import { NextRequest, NextResponse } from "next/server";
import { requireAdminUserForApi } from "@/lib/admin/access";
import { listManagedUsers, type ManagedUserStatus } from "@/lib/admin/users";

export const dynamic = "force-dynamic";

const validStatuses = new Set<ManagedUserStatus>(["pending", "active", "expired", "revoked"]);

export async function GET(request: NextRequest) {
  const { errorResponse } = await requireAdminUserForApi();
  if (errorResponse) {
    return errorResponse;
  }

  const rawStatus = request.nextUrl.searchParams.get("status");
  const status =
    rawStatus && validStatuses.has(rawStatus as ManagedUserStatus)
      ? (rawStatus as ManagedUserStatus)
      : undefined;

  try {
    const users = await listManagedUsers(status);
    return NextResponse.json({ users });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load users."
      },
      { status: 500 }
    );
  }
}
