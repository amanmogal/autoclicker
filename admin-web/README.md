# Bhada Approval Admin

Standalone Next.js admin panel for manual driver approval and 30-day renewal control.

This app is separate from the Android app. It uses Supabase Auth as the source of truth and lets a non-technical admin:

- see confirmed user emails
- approve a new user
- renew access for 30 days
- revoke access
- track pending, active, expired, and revoked accounts

## What It Controls

The panel writes these fields into Supabase Auth `app_metadata`:

- `approved`
- `approval_status`
- `subscription_status`
- `subscription_started_at`
- `subscription_expires_at`
- `last_admin_action_at`

The Android app reads those values and locks immediately when access expires or is revoked.

## Environment Variables

Copy `.env.example` to `.env.local` and fill in:

```bash
cp .env.example .env.local
```

Required variables:

- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`

## Local Development

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Verification

```bash
npm run typecheck
npm run lint
npm run build
```

## Admin Access

Only Supabase users with:

```text
app_metadata.role = "admin"
```

can use this panel.

For the first admin, set that field manually in Supabase Auth.

## Deployment

This app is ready to deploy as a normal Next.js app, including on Vercel.

Minimum deployment setup:

1. Set the project root to `admin-web/`
2. Add the three environment variables above
3. Deploy

The server-side routes use the service-role key. Never expose that key in browser code.
