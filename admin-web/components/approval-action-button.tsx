"use client";

import { startTransition, useState, useTransition } from "react";
import { useRouter } from "next/navigation";

type ApprovalAction = "approve" | "reject";

interface ApprovalActionButtonProps {
  action: ApprovalAction;
  label: string;
}

export function ApprovalActionButton({
  action,
  label,
  userId
}: ApprovalActionButtonProps & { userId: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, startUiTransition] = useTransition();

  const handleClick = () => {
    setError(null);
    startUiTransition(() => {
      startTransition(async () => {
        const response = await fetch(`/api/admin/users/${userId}/${action}`, {
          method: "POST"
        });

        if (!response.ok) {
          const payload = (await response.json().catch(() => null)) as { error?: string } | null;
          setError(payload?.error ?? "Action failed.");
          return;
        }

        router.refresh();
      });
    });
  };

  return (
    <div className="action-stack">
      <button
        className={action === "approve" ? "primary-button" : "danger-button"}
        disabled={pending}
        onClick={handleClick}
        type="button"
      >
        {pending ? "Working..." : label}
      </button>
      {error ? <p className="inline-error">{error}</p> : null}
    </div>
  );
}
