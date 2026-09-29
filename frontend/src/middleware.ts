import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

let clerkHandler: any = null;

if (process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY) {
  try {
    const { clerkMiddleware, createRouteMatcher } = require('@clerk/nextjs/server');
    const isProtectedRoute = createRouteMatcher([
      '/dashboard(.*)',
      '/api/watchlist(.*)',
      '/api/alerts(.*)',
      '/api/engine(.*)',
    ]);

    clerkHandler = clerkMiddleware(async (auth: any, req: any) => {
      if (isProtectedRoute(req)) {
        await auth.protect();
      }
    });
  } catch (e) {
    console.warn('Clerk middleware initialization skipped:', e);
  }
}

export default async function middleware(req: NextRequest, event: any) {
  if (clerkHandler) {
    return clerkHandler(req, event);
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
