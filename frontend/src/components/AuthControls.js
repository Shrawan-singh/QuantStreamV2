/**
 * ==============================================================================
 * Authentication Controls Component (frontend/src/components/AuthControls.js)
 * ==============================================================================
 *
 * This component provides sign-in, sign-up, and signed-in user controls
 * for the QuantStream application. It conditionally displays different UI
 * based on the user's authentication state.
 *
 * WHAT IT DOES:
 * - Shows "Sign In" and "Sign Up" buttons for unauthenticated users
 * - Shows a user profile menu for authenticated users
 * - Handles responsive design for mobile and desktop
 * ==============================================================================
 */

'use client';

import React from 'react';
import { SignInButton, SignUpButton, Show, UserButton } from '@clerk/nextjs';

export default function AuthControls() {
  const hasClerkKey = Boolean(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY);

  if (!hasClerkKey) {
    return (
      <div className="flex-row items-center gap-xs">
        <span className="badge badge-accent font-bold" style={{ fontSize: '0.72rem', padding: '4px 8px', letterSpacing: '0.5px' }}>
          SIMULATION SESSION
        </span>
      </div>
    );
  }

  return (
    <div className="flex-row items-center gap-sm">
      {/* Show sign-in and sign-up buttons when user is NOT authenticated */}
      <Show when="signed-out">
        <SignInButton mode="modal">
          <button className="btn btn-secondary" style={{ fontSize: '0.85rem', padding: '0.5rem 1rem' }}>
            Sign In
          </button>
        </SignInButton>
        <SignUpButton mode="modal">
          <button className="btn btn-accent" style={{ fontSize: '0.85rem', padding: '0.5rem 1rem' }}>
            Sign Up
          </button>
        </SignUpButton>
      </Show>

      {/* Show user profile menu when user IS authenticated */}
      <Show when="signed-in">
        <UserButton
          afterSignOutUrl="/"
          appearance={{
            elements: {
              avatarBox: {
                width: '36px',
                height: '36px',
                borderRadius: '4px',
              },
            },
          }}
        />
      </Show>
    </div>
  );
}
