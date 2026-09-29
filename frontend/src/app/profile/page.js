'use client'

import Link from 'next/link'
import { ArrowLeft, ArrowUpRight } from 'lucide-react'
import { SignOutButton, UserProfile, useUser } from '@clerk/nextjs'
import styles from './profile.module.css'

const clerkAppearance = {
  variables: {
    colorBackground: '#0c121b',
    colorInputBackground: '#111a27',
    colorInputText: '#f8fafc',
    colorNeutral: '#9aabc0',
    colorPrimary: '#06b6d4',
    colorText: '#f8fafc',
  },
  elements: {
    card: { background: '#0c121b', border: '1px solid rgba(148, 163, 184, 0.16)', boxShadow: 'none' },
    navbar: { background: '#0c121b' },
    navbarButton: { color: '#9aabc0' },
    navbarButton__active: { color: '#4cc9f0' },
    profileSectionPrimaryButton: { color: '#4cc9f0' },
    rootBox: { width: '100%' },
  },
}

function ClerkProfileContent() {
  const { isLoaded, user } = useUser()

  if (!isLoaded) {
    return <main className={styles.page}><p className={styles.loading}>Loading Clerk profile…</p></main>
  }

  if (!user) {
    return (
      <main className={styles.page}>
        <p className={styles.loading}>Profile unavailable. <Link href="/sign-in">Sign in</Link></p>
      </main>
    )
  }

  const name = user.fullName || [user.firstName, user.lastName].filter(Boolean).join(' ') || 'Name not set'
  const email = user.primaryEmailAddress?.emailAddress || 'No primary email on file'
  const createdDate = user.createdAt ? new Date(user.createdAt).toISOString().slice(0, 10) : 'Not available'

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <Link className={styles.brand} href="/">
          <span className={styles.brandMark}>Q</span>
          <span>QUANT<span>STREAM</span><i>/ ACCOUNT</i></span>
        </Link>
        <Link className={styles.backLink} href="/dashboard"><ArrowLeft size={15} /> Back to dashboard</Link>
      </header>

      <div className={styles.content}>
        <div className={styles.pageHeading}>
          <div>
            <p className={styles.eyebrow}>ACCOUNT / CLERK IDENTITY</p>
            <h1>Your profile</h1>
            <p>Account details and profile management for your QuantStream session.</p>
          </div>
          <span className={styles.sessionBadge}><i /> AUTHENTICATED</span>
        </div>

        <section className={styles.identityPanel} aria-labelledby="identity-heading">
          <div className={styles.identityTop}>
            <div className={styles.identityPerson}>
              <span className={styles.avatar}>
                {user.imageUrl ? (
                  <img src={user.imageUrl} alt={name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ fontSize: '1.4rem', fontWeight: 'bold', color: 'var(--accent-blue)' }}>{name.charAt(0).toUpperCase()}</span>
                )}
              </span>
              <div className={styles.personCopy}>
                <span className={styles.eyebrow}>SIGNED-IN USER</span>
                <h2 id="identity-heading">{name}</h2>
                <p>{email}</p>
              </div>
            </div>
            <div className={styles.profileActions}>
              <a className={styles.manageButton} href="#account-management">
                Manage Account <ArrowUpRight size={15} />
              </a>
              <SignOutButton redirectUrl="/">
                <button className={styles.signOutButton} type="button">Sign Out</button>
              </SignOutButton>
            </div>
          </div>

          <dl className={styles.accountDetails}>
            <div><dt>PRIMARY EMAIL</dt><dd>{email}</dd></div>
            <div><dt>ACCOUNT CREATED</dt><dd>{createdDate}</dd></div>
            <div className={styles.userId}><dt>CLERK USER ID</dt><dd>{user.id}</dd></div>
          </dl>
        </section>

        <section className={styles.managementSection} id="account-management" aria-labelledby="management-heading">
          <div className={styles.managementHeading}>
            <div>
              <p className={styles.eyebrow}>SECURE ACCOUNT SETTINGS</p>
              <h2 id="management-heading">Manage Account</h2>
            </div>
            <p>Profile, sign-in methods, and security are managed directly by Clerk.</p>
          </div>
          <div className={styles.clerkProfile}>
            <UserProfile appearance={clerkAppearance} />
          </div>
        </section>
      </div>
    </main>
  )
}

export default function ProfilePage() {
  const hasClerkKey = Boolean(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY)

  if (!hasClerkKey) {
    return (
      <main className={styles.page}>
        <header className={styles.header}>
          <Link className={styles.brand} href="/">
            <span className={styles.brandMark}>Q</span>
            <span>QUANT<span>STREAM</span><i>/ ACCOUNT</i></span>
          </Link>
          <Link className={styles.backLink} href="/dashboard"><ArrowLeft size={15} /> Back to dashboard</Link>
        </header>
        <div className={styles.content}>
          <div className={styles.pageHeading}>
            <div>
              <p className={styles.eyebrow}>SIMULATION MODE</p>
              <h1>Simulation Profile</h1>
              <p>Clerk authentication keys are not currently configured in the environment.</p>
            </div>
            <span className={styles.sessionBadge}><i /> SIMULATED</span>
          </div>
          <section className={styles.identityPanel}>
            <p style={{ color: 'var(--text-muted)', lineHeight: '1.6' }}>
              You are running the high-frequency QuantStream simulation cockpit in standalone mode. To enable full Clerk sign-in and user profiles, provide <code style={{ color: 'var(--accent-blue)' }}>NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY</code> and <code style={{ color: 'var(--accent-blue)' }}>CLERK_SECRET_KEY</code>.
            </p>
            <div style={{ marginTop: '20px' }}>
              <Link href="/dashboard" className="btn btn-accent" style={{ display: 'inline-flex', padding: '8px 16px', textDecoration: 'none' }}>
                Open Trading Cockpit
              </Link>
            </div>
          </section>
        </div>
      </main>
    )
  }

  return <ClerkProfileContent />
}