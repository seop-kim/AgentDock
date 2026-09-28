import type { ReactNode } from 'react';
import styles from './layout.module.css';
import './globals.css';

export const metadata = {
  title: 'AgentDock',
  description: 'Multi-Agent Organization Platform',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <nav className={styles.nav}>
          <a href="/">Dashboard</a>
          <a href="/agents">Agents</a>
          <a href="/workspaces">Workspaces</a>
          <a href="/providers">AI Providers</a>
        </nav>
        <main className={styles.main}>{children}</main>
      </body>
    </html>
  );
}
