'use client'

import Link from 'next/link'
import { useAuth } from '@clerk/nextjs'
import { ArrowRight } from 'lucide-react'
import styles from './landing.module.css'

export default function LaunchLink() {
  const { isLoaded, isSignedIn } = useAuth()
  const href = isLoaded && isSignedIn ? '/dashboard' : '/sign-in'

  return (
    <Link className={styles.launchButton} href={href}>
      Launch QuantStream <ArrowRight size={17} aria-hidden="true" />
    </Link>
  )
}