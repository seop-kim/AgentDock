import type { ReactNode } from 'react';

export const metadata = {
  title: 'AgentDock',
  description: 'Multi-Agent Organization Platform',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body style={{ fontFamily: 'system-ui, sans-serif', margin: 0 }}>
        <nav style={{ display: 'flex', gap: 16, padding: 16, borderBottom: '1px solid #ddd' }}>
          <a href="/">Dashboard</a>
          <a href="/agents">Agents</a>
          <a href="/workspaces">Workspaces</a>
          <a href="/providers">AI Providers</a>
        </nav>
        <main style={{ padding: 24 }}>{children}</main>
      </body>
    </html>
  );
}
