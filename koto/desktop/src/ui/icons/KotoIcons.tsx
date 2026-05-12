/**
 * Фирменные иконки Koto — геометрия «уши + срезанный шестиугольник», не стоковые Heroicons.
 * Все 24×24, stroke 1.75, скруглённые стыки; заливки только там, где нужен акцент.
 */
import { useId } from "react";
import type { SVGProps } from "react";

const stroke = 1.75;

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function baseProps(size: number): SVGProps<SVGSVGElement> {
  return {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    xmlns: "http://www.w3.org/2000/svg",
    "aria-hidden": true,
  };
}

/** Марк в rail: шестиугольник + два «лисьих» уха */
export function KotoRailMark({ size = 28, className, ...rest }: IconProps) {
  const gid = useId().replace(/:/g, "");
  const gradId = `koto-rail-g-${gid}`;
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M12 3.2 18.2 6.4v6.4L12 16 5.8 12.8V6.4L12 3.2z"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinejoin="round"
        fill={`url(#${gradId})`}
      />
      <path
        d="M8.2 4.1 7 1.9 9.4 3.4 12 2.6l2.6.8 2.4-1.5-1.2 2.2"
        stroke="currentColor"
        strokeWidth={stroke * 0.85}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
      <defs>
        <linearGradient id={gradId} x1="6" y1="3" x2="18" y2="15" gradientUnits="userSpaceOnUse">
          <stop stopColor="var(--color-accent-soft, #7289da)" />
          <stop offset="1" stopColor="var(--color-accent-deep, #4f5dcc)" />
        </linearGradient>
      </defs>
    </svg>
  );
}

/** Боты — корпус-гекс + «антенна» и глаз-линза */
export function KotoIconBots({ size = 22, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M12 5.5 16.5 8v5L12 15.5 7.5 13V8L12 5.5z"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinejoin="round"
      />
      <path d="M12 3v2.2M10 4.2h4" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" />
      <circle cx="10" cy="10.5" r="1.1" fill="currentColor" />
      <circle cx="14" cy="10.5" r="1.1" fill="currentColor" />
      <path d="M9.5 12.5h5" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" />
    </svg>
  );
}

/** Вложение / «камера» — ромб-объектив в рамке-срезе */
export function KotoIconCapture({ size = 22, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M6.5 8.5h3l1.2-2h3.6l1.2 2h2.5v8.5h-12V8.5z"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinejoin="round"
      />
      <path
        d="M12 11.2 14.3 12.8 12 14.4 9.7 12.8 12 11.2z"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12.8" r="2.8" stroke="currentColor" strokeWidth={1.35} />
    </svg>
  );
}

/** Новый чат — перо + хвост-крюк Koto */
export function KotoIconCompose({ size = 22, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M4 17.5V20h2.5l9.8-9.8-2.5-2.5L4 17.5z"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinejoin="round"
      />
      <path
        d="M14.8 6.7l2.5 2.5 2.2-2.2c.4-.4.4-1 0-1.4l-1.1-1.1c-.4-.4-1-.4-1.4 0l-2.2 2.2z"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinejoin="round"
      />
      <path d="M6 20.5c2-1.2 3.5-3 4.5-5" stroke="currentColor" strokeWidth={1.35} strokeLinecap="round" opacity={0.55} />
    </svg>
  );
}

export function KotoIconSearch({ size = 18, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <circle cx="10" cy="10" r="5.25" stroke="currentColor" strokeWidth={stroke} />
      <path d="M14.2 14.2 20 20" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" />
      <path d="M7.5 10h5" stroke="currentColor" strokeWidth={1.25} strokeLinecap="round" opacity={0.35} />
    </svg>
  );
}

export function KotoIconArchive({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M4 8.5h16v10a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-10z"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinejoin="round"
      />
      <path d="M2 5.5h20v3H2v-3z" stroke="currentColor" strokeWidth={stroke} strokeLinejoin="round" />
      <path d="M12 11v4.5M9.5 13.2 12 11l2.5 2.2" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** Настройки — узел + кольцо (узнаваемо, без стоковой «классической» шестерни) */
/** Микрофон (заглушка под Discord-панель) */
export function KotoIconMic({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3z"
        stroke="currentColor"
        strokeWidth={1.65}
        strokeLinejoin="round"
      />
      <path d="M8 11v1a4 4 0 0 0 8 0v-1M12 18v2.5" stroke="currentColor" strokeWidth={1.65} strokeLinecap="round" />
    </svg>
  );
}

/** Наушники (заглушка) */
export function KotoIconHeadphones({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M4 14v3a2 2 0 0 0 2 2h1M20 14v3a2 2 0 0 1-2 2h-1M4 14a8 8 0 0 1 16 0"
        stroke="currentColor"
        strokeWidth={1.65}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function KotoIconSettingsCog({ size = 22, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <circle cx="12" cy="12" r="3.2" stroke="currentColor" strokeWidth={stroke} />
      <path
        d="M12 2.5v2.4M12 19.1v2.4M3.8 12h2.4M17.8 12h2.4M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M5.6 18.4l1.7-1.7M16.7 7.3l1.7-1.7"
        stroke="currentColor"
        strokeWidth={1.65}
        strokeLinecap="round"
      />
      <path
        d="M12 6.5 14.8 8.1 12 9.7 9.2 8.1 12 6.5z"
        stroke="currentColor"
        strokeWidth={1.25}
        strokeLinejoin="round"
        opacity={0.45}
      />
    </svg>
  );
}

/** Копирование — два слоя + угол «выреза» */
export function KotoIconCopyId({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M8.5 7.5h8a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5h-8a1.5 1.5 0 0 1-1.5-1.5V9A1.5 1.5 0 0 1 8.5 7.5z"
        stroke="currentColor"
        strokeWidth={1.55}
        strokeLinejoin="round"
      />
      <path
        d="M6 5.5h8a1.1 1.1 0 0 1 1.1 1.1V14"
        stroke="currentColor"
        strokeWidth={1.35}
        strokeLinecap="round"
        opacity={0.45}
      />
    </svg>
  );
}

/** Уведомления — колокол + маячок */
export function KotoIconBell({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M12 4.2a4.2 4.2 0 0 0-4.2 4.2v3.1c0 .6-.2 1.2-.5 1.7l-.4.6h10.2l-.4-.6c-.3-.5-.5-1.1-.5-1.7V8.4A4.2 4.2 0 0 0 12 4.2z"
        stroke="currentColor"
        strokeWidth={1.55}
        strokeLinejoin="round"
      />
      <path d="M9.2 17.2h5.6a1.6 1.6 0 0 1-5.6 0z" stroke="currentColor" strokeWidth={1.35} strokeLinejoin="round" />
      <circle cx="16.2" cy="6.3" r="1.35" fill="#7289da" />
    </svg>
  );
}

/** Приватность — щит + «замок» внутри */
export function KotoIconShieldLock({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M12 3.2 18.5 6.2v5.4c0 3.4-2.4 6.5-6.5 8.4-4.1-1.9-6.5-5-6.5-8.4V6.2L12 3.2z"
        stroke="currentColor"
        strokeWidth={1.55}
        strokeLinejoin="round"
      />
      <path
        d="M10.2 11.2V9.6a1.8 1.8 0 1 1 3.6 0v1.6M10 14.2h4a.8.8 0 0 1 .8.8v.6H9.2v-.6a.8.8 0 0 1 .8-.8z"
        stroke="currentColor"
        strokeWidth={1.35}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Фокус — прицельная рамка */
export function KotoIconFocusFrame({ size = 20, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path d="M7 4.5H4.5V7M17 4.5h2.5V7M7 19.5H4.5V17M17 19.5h2.5V17" stroke="currentColor" strokeWidth={1.55} strokeLinecap="round" />
      <rect x="8.2" y="8.2" width="7.6" height="7.6" rx="1.6" stroke="currentColor" strokeWidth={1.45} />
      <circle cx="12" cy="12" r="1.35" fill="currentColor" opacity={0.35} />
    </svg>
  );
}

/** Друзья — два «кото»-силуэта рядом */
export function KotoIconFriends({ size = 22, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M8.2 6.2 11.2 8v3.6L8.2 13.6 5.2 11.6V8L8.2 6.2z"
        stroke="currentColor"
        strokeWidth={1.55}
        strokeLinejoin="round"
      />
      <path
        d="M15.8 6.2 18.8 8v3.6L15.8 13.6 12.8 11.6V8l3-1.8z"
        stroke="currentColor"
        strokeWidth={1.55}
        strokeLinejoin="round"
        opacity={0.92}
      />
      <path d="M8.2 4.4v1.2M15.8 4.4v1.2" stroke="currentColor" strokeWidth={1.2} strokeLinecap="round" opacity={0.5} />
    </svg>
  );
}

/** Шеврон раскрытия панели профиля (сайдбар). */
export function KotoIconChevronDown({ size = 18, className, ...rest }: IconProps) {
  return (
    <svg {...baseProps(size)} className={className} {...rest}>
      <path
        d="M6.5 9.25 12 14.75 17.5 9.25"
        stroke="currentColor"
        strokeWidth={stroke}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
