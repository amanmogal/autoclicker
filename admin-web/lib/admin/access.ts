import { redirect } from "next/navigation";
import type { User } from "@supabase/supabase-js";
import { createServerSupabaseClient } from "@/lib/supabase/server";
import { userIsAdmin } from "@/lib/admin/users";

export async function getCurrentAuthenticatedUser(): Promise<User | null> {
  const supabase = await createServerSupabaseClient();
  const { data, error } = await supabase.auth.getUser();

  if (error) {
    return null;
  }

  return data.user ?? null;
}

export async function requireLoggedInUserForPage(): Promise<User> {
  const user = await getCurrentAuthenticatedUser();
  if (!user) {
    redirect("/login");
  }
  return user;
}

export async function requireAdminUserForPage(): Promise<User> {
  const user = await requireLoggedInUserForPage();
  if (!userIsAdmin(user)) {
    redirect("/forbidden");
  }
  return user;
}

export async function requireAdminUserForApi(): Promise<{
  errorResponse: null | Response;
  user: User | null;
}> {
  const user = await getCurrentAuthenticatedUser();
  if (!user) {
    return {
      errorResponse: Response.json({ error: "Authentication required." }, { status: 401 }),
      user: null
    };
  }

  if (!userIsAdmin(user)) {
    return {
      errorResponse: Response.json({ error: "Admin role required." }, { status: 403 }),
      user: null
    };
  }

  return {
    errorResponse: null,
    user
  };
}
