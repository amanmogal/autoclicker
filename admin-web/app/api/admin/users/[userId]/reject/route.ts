import { NextResponse } from "next/server";
import { requireAdminUserForApi } from "@/lib/admin/access";
import { updateUserApproval } from "@/lib/admin/users";

export const dynamic = "force-dynamic";

export async function POST(
  _request: Request,
  context: { params: Promise<{ userId: string }> }
) {
  const { errorResponse } = await requireAdminUserForApi();
  if (errorResponse) {
    return errorResponse;
  }

  const { userId } = await context.params;

  try {
    const user = await updateUserApproval(userId, "reject");
    return NextResponse.json({ user });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Rejection failed."
      },
      { status: 500 }
    );
  }
}
