"use client";

import { startTransition, useTransition } from "react";
import { useRouter } from "next/navigation";
import { createBrowserSupabaseClient } from "@/lib/supabase/browser";

interface SignOutButtonProps {
  className?: string;
  label?: string;
}

export function SignOutButton({
  className,
  label = "Sign out"
}: SignOutButtonProps) {
  const router = useRouter();
  const [pending, startUiTransition] = useTransition();

  const handleSignOut = () => {
    startUiTransition(() => {
      startTransition(async () => {
        const supabase = createBrowserSupabaseClient();
        await supabase.auth.signOut();
        router.replace("/login");
        router.refresh();
      });
    });
  };

  return (
    <button className={className ?? "ghost-button"} disabled={pending} onClick={handleSignOut} type="button">
      {pending ? "Signing out..." : label}
    </button>
  );
}
