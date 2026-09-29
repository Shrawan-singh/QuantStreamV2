import Link from 'next/link'
import { SignIn } from '@clerk/nextjs'

export default function SignInPage() {
  const hasClerkKey = Boolean(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY)

  if (!hasClerkKey) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '80vh', gap: '1rem', color: '#f8fafc', padding: '2rem', textAlign: 'center' }}>
        <h2>Simulation Active</h2>
        <p style={{ color: '#9aabc0', maxWidth: '420px' }}>Clerk keys are not configured. You can use the full simulation cockpit without signing in.</p>
        <Link href="/dashboard" style={{ background: '#06b6d4', color: '#000', padding: '8px 16px', borderRadius: '4px', textDecoration: 'none', fontWeight: 700 }}>
          Go to Trading Cockpit
        </Link>
      </div>
    )
  }

  return <SignIn fallbackRedirectUrl="/dashboard" />
}