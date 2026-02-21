import "./globals.css";
import Link from "next/link";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <main className="mx-auto max-w-5xl p-6">
          <header className="mb-8 flex items-center justify-between">
            <h1 className="text-xl font-semibold">Viral Proxy Lab</h1>
            <nav className="flex gap-4 text-sm text-slate-300">
              <Link href="/upload">Upload</Link>
              <Link href="/history">History</Link>
            </nav>
          </header>
          {children}
        </main>
      </body>
    </html>
  );
}
