import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { clerkMiddleware, createRouteMatcher } from '@clerk/nextjs/server';

const hasClerkKeys = Boolean(
  process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY && process.env.CLERK_SECRET_KEY
);

const isProtectedRoute = createRouteMatcher([
  '/dashboard(.*)',
  '/api/watchlist(.*)',
  '/api/alerts(.*)',
  '/api/engine(.*)',
]);

const clerkHandler = hasClerkKeys
  ? clerkMiddleware(async (auth: any, req: any) => {
      if (isProtectedRoute(req)) {
        await auth.protect();
      }
    })
  : null;

export default async function middleware(req: NextRequest, event: any) {
  if (clerkHandler) {
    try {
      return await clerkHandler(req, event);
    } catch (err) {
      console.warn('Clerk middleware error fallback:', err);
      return NextResponse.next();
    }
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    '/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest))(?:.*)|api|trpc)(.*)',
    '/(api|trpc)(.*)',
    '/__clerk/:path*',
  ],
};

