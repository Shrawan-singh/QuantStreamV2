/**
 * ==============================================================================
 * Clerk Auth Callback Page (frontend/src/app/auth-callback/page.js)
 * ==============================================================================
 *
 * This page handles the OAuth callback from Clerk after a user signs in or signs up.
 * Clerk automatically redirects users here, and this page redirects them to the
 * dashboard once the authentication is complete.
 * ==============================================================================
 */

'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

function ClerkCallbackInner({ router }) {
  const { useAuth } = require('@clerk/nextjs');
  const { isLoaded, isSignedIn } = useAuth();

  useEffect(() => {
    if (isLoaded && isSignedIn) {
      router.push('/');
    } else if (isLoaded && !isSignedIn) {
      router.push('/');
    }
  }, [isLoaded, isSignedIn, router]);

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      backgroundColor: '#0a0e27',
      color: '#ffffff',
    }}>
      <div style={{ textAlign: 'center' }}>
        <h2>Completing your sign-in...</h2>
        <p style={{ marginTop: '1rem', opacity: 0.7 }}>Please wait while we authenticate you.</p>
      </div>
    </div>
  );
}

export default function AuthCallbackPage() {
  const router = useRouter();
  const hasClerkKey = Boolean(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY);

  useEffect(() => {
    if (!hasClerkKey) {
      router.push('/');
    }
  }, [hasClerkKey, router]);

  if (!hasClerkKey) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        backgroundColor: '#0a0e27',
        color: '#ffffff',
      }}>
        <p>Redirecting to QuantStream Dashboard...</p>
      </div>
    );
  }

  return <ClerkCallbackInner router={router} />;
}
