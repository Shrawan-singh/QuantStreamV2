/**
 * ==============================================================================
 * Root Application Layout (frontend/src/app/layout.js)
 * ==============================================================================
 *
 * WHAT IS THIS FILE FOR? (Plain English):
 * In Next.js (the modern React framework we use for the website), `layout.js`
 * is the master "frame" or "skeleton" that wraps around every single page.
 *
 * Think of it like a picture frame:
 * - The frame (HTML tags, CSS styles, web fonts, browser tab title) stays the
 *   same all the time.
 * - The picture inside ({children}) changes depending on what page the user visits!
 * ==============================================================================
 */

import "./globals.css";

/**
 * METADATA:
 * Tells web browsers and search engines what this website is called.
 * This sets the text that appears on your browser's tab at the top of your screen!
 */
export const metadata = {
  title: "QuantStream — Real-Time Market Data Streaming & Quantitative Signal Engine",
  description: "Real-time market data streaming pipeline, bounded concurrency, in-memory technical indicators, and explainable conviction scoring engine.",
};

/**
 * RootLayout Component:
 * Wraps all visual components inside standard <html> and <body> tags.
 *
 * Notice the Google Font `<link>` tag in `<head>`:
 * It downloads "Material Symbols Outlined", which gives our trading dashboard
 * sleek financial icons (candlestick charts, bells, server racks, speedometer gauges)
 * without needing slow-loading image files!
 */
export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200&display=swap"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}

