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
import { useAuth } from '@clerk/nextjs';

export default function AuthCallbackPage() {
  const router = useRouter();
  const { isLoaded, isSignedIn } = useAuth();

  useEffect(() => {
    if (isLoaded && isSignedIn) {
      // Redirect to dashboard after successful sign-in
      router.push('/dashboard');
    } else if (isLoaded && !isSignedIn) {
      // Redirect back to home if not signed in
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
