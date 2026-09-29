'use client';

import { SignInButton, UserButton, useAuth } from '@clerk/nextjs';

function ClerkControls() {
  const { isLoaded, isSignedIn } = useAuth();

  if (!isLoaded) return null;

  if (!isSignedIn) {
    return (
      <SignInButton mode="modal">
        <button
          type="button"
          style={{
            background: 'var(--bg-elevated)',
            border: '1px solid var(--border-color)',
            borderRadius: '4px',
            color: 'var(--text-primary)',
            cursor: 'pointer',
            fontSize: '0.75rem',
            fontWeight: 700,
            padding: '7px 12px',
          }}
        >
          Sign In
        </button>
      </SignInButton>
    );
  }

  return (
    <UserButton
      appearance={{
        variables: {
          colorBackground: '#0c121b',
          colorInputBackground: '#111a27',
          colorInputText: '#f8fafc',
          colorNeutral: '#9aabc0',
          colorPrimary: '#06b6d4',
          colorText: '#f8fafc',
        },
        elements: {
          userButtonAvatarBox: { height: '32px', width: '32px' },
          userButtonTrigger: { height: '32px', width: '32px' },
        },
      }}
    />
  );
}

export default function AuthControls() {
  const hasClerkKey = Boolean(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY);

  if (!hasClerkKey) {
    return (
      <div className="flex-row items-center gap-xs">
        <span
          className="badge badge-accent font-bold"
          style={{ fontSize: '0.72rem', padding: '4px 8px', letterSpacing: '0.5px' }}
        >
          SIMULATION ACTIVE
        </span>
      </div>
    );
  }

  return <ClerkControls />;
}