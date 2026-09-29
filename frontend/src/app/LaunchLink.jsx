'use client'

import Link from 'next/link'
import { useAuth } from '@clerk/nextjs'
import { ArrowRight } from 'lucide-react'
import styles from './landing.module.css'

function ClerkLaunchLink() {
  const { isLoaded, isSignedIn } = useAuth()
  const href = isLoaded && isSignedIn ? '/dashboard' : '/sign-in'

  return (
    <Link className={styles.launchButton} href={href}>
      Launch QuantStream <ArrowRight size={17} aria-hidden="true" />
    </Link>
  )
}

export default function LaunchLink() {
  const hasClerkKey = Boolean(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY)

  if (!hasClerkKey) {
    return (
      <Link className={styles.launchButton} href="/dashboard">
        Launch QuantStream <ArrowRight size={17} aria-hidden="true" />
      </Link>
    )
  }

  return <ClerkLaunchLink />
}