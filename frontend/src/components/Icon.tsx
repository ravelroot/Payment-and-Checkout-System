const paths: Record<string, string> = {
  search: "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm10 2-4.35-4.35",
  bag: "M6 7h12l1 13H5L6 7Zm3 0a3 3 0 0 1 6 0",
  user: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0",
  box: "M21 8 12 3 3 8v8l9 5 9-5V8ZM3 8l9 5 9-5M12 13v8",
  grid: "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
  chart: "M4 20V10m6 10V4m6 16v-7m6 7H2",
  tag: "M20 12 12 20l-8-8V4h8l8 8ZM8 8h.01",
  users: "M16 19a4 4 0 0 0-8 0M12 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7 8a3 3 0 0 0-3-3m1-4a2.5 2.5 0 1 0 0-5M5 19a3 3 0 0 1 3-3m-1-4a2.5 2.5 0 1 1 0-5",
  receipt: "M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6m-6 4h6m-6 4h4",
  layers: "m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5m-18 4 9 5 9-5",
  ticket: "M3 8a2 2 0 0 0 0 4v4h18v-4a2 2 0 0 1 0-4V4H3v4Zm11-4v16",
  logout: "M15 4h4v16h-4M10 8l-4 4 4 4m-4-4h11",
  plus: "M12 5v14M5 12h14",
  minus: "M5 12h14",
  close: "M6 6l12 12M18 6 6 18",
  check: "m5 12 5 5 9-10",
  arrowLeft: "M19 12H5m6-6-6 6 6 6",
  arrowRight: "M5 12h14m-6-6 6 6-6 6",
  chevronDown: "m6 9 6 6 6-6",
  trash: "M4 7h16M10 11v6m4-6v6M6 7l1 13h10l1-13M9 7V4h6v3",
  edit: "M4 20h4L19 9l-4-4L4 16v4Zm9-13 4 4",
  card: "M3 6h18v12H3zM3 10h18M7 15h4",
  pix: "M12 3 21 12l-9 9-9-9 9-9Zm-4 9 4-4 4 4-4 4-4-4Z",
  barcode: "M4 5v14M7 5v14M10 5v14M14 5v14M16 5v14M20 5v14",
  truck: "M3 6h11v10H3zM14 9h4l3 3v4h-7M7 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm10 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z",
  clock: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13v5l3 2",
  home: "M3 11 12 4l9 7v9h-6v-6H9v6H3v-9Z",
  copy: "M8 8h12v12H8zM4 16V4h12",
  external: "M14 4h6v6m0-6-9 9M18 14v6H4V6h6",
  shield: "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Z",
  store: "M4 9h16l-1-5H5L4 9Zm0 0v11h16V9M9 20v-6h6v6",
  menu: "M4 7h16M4 12h16M4 17h16",
  filter: "M4 6h16M7 12h10M10 18h4",
  refresh: "M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5",
  eye: "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z",
  lock: "M6 11h12v10H6zM8 11V8a4 4 0 0 1 8 0v3"
};

export type IconName = keyof typeof paths;

export function Icon({ name, size = 20, stroke = 1.7 }: { name: IconName | string; size?: number; stroke?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={stroke}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d={paths[name] ?? ""} />
    </svg>
  );
}
