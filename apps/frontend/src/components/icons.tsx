import type { ReactNode } from 'react';

/** 24x24 선(stroke) 아이콘. 색은 currentColor 를 따르므로 테마 토큰이 그대로 적용된다. */
function Icon({ children, size = 20 }: { children: ReactNode; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export const MenuIcon = () => (
  <Icon>
    <path d="M4 6h16M4 12h16M4 18h16" />
  </Icon>
);

export const DashboardIcon = () => (
  <Icon>
    <rect x="3" y="3" width="7" height="9" rx="1" />
    <rect x="14" y="3" width="7" height="5" rx="1" />
    <rect x="14" y="12" width="7" height="9" rx="1" />
    <rect x="3" y="16" width="7" height="5" rx="1" />
  </Icon>
);

export const FilterIcon = () => (
  <Icon>
    <path d="M4 5h16l-6 7v6l-4 2v-8L4 5z" />
  </Icon>
);

export const SettingsIcon = () => (
  <Icon>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
  </Icon>
);

export const FolderIcon = () => (
  <Icon>
    <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />
  </Icon>
);

export const AlertIcon = () => (
  <Icon>
    <path d="M12 4l8.5 15H3.5L12 4z" />
    <path d="M12 10v4M12 17h.01" />
  </Icon>
);

export const PlusIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);

export const ChevronUpIcon = () => (
  <Icon>
    <path d="M6 15l6-6 6 6" />
  </Icon>
);

export const ClipIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M21.4 11.1l-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.9l8.5-8.5" />
  </Icon>
);

export const FileIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M14 3v4a1 1 0 0 0 1 1h4" />
    <path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2z" />
  </Icon>
);

/** 크게 보기(왼쪽으로 넓힌다) */
export const ChevronLeftIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M15 6l-6 6 6 6" />
  </Icon>
);

/** 원래 크기로(오른쪽으로 돌아간다) */
export const ChevronRightIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M9 6l6 6-6 6" />
  </Icon>
);

export const ChevronDownIcon = () => (
  <Icon>
    <path d="M6 9l6 6 6-6" />
  </Icon>
);

/** 에이전트 연결 설정(플러그) */
export const PlugIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M9 3v5M15 3v5" />
    <path d="M6 8h12v3a6 6 0 0 1-12 0V8z" />
    <path d="M12 17v4" />
  </Icon>
);

/** 테마(명암 원) */
export const ThemeIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor" stroke="none" />
  </Icon>
);

/** 라이트 테마(해) */
export const SunIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4" />
  </Icon>
);

/** 다크 테마(달) */
export const MoonIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
  </Icon>
);

/** 터미널/CLI */
export const TerminalIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <path d="M7 9l3 3-3 3" />
    <path d="M13 15h4" />
  </Icon>
);

/** 삭제(휴지통) */
export const TrashIcon = ({ size = 20 }: { size?: number }) => (
  <Icon size={size}>
    <path d="M4 7h16" />
    <path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7" />
    <path d="M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12" />
    <path d="M10 11v6M14 11v6" />
  </Icon>
);