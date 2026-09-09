import "./globals.css";

export const metadata = {
  title: "QuantStream — Real-Time Market Data Streaming & Quantitative Signal Engine",
  description: "Real-time market data streaming pipeline, bounded concurrency, in-memory technical indicators, and explainable conviction scoring engine.",
};

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
