// WatchColors - exact match from WatchColors.swift
export const colors = {
  gray950: "rgb(10, 10, 11)",
  gray900: "rgb(24, 24, 27)",
  gray800: "rgb(39, 39, 42)",
  gray700: "rgb(63, 63, 70)",
  gray600: "rgb(82, 82, 91)",
  gray500: "rgb(113, 113, 122)",
  gray400: "rgb(161, 161, 170)",
  gray300: "rgb(212, 212, 216)",

  blue500: "rgb(59, 130, 246)",
  blue600: "rgb(37, 99, 235)",
  blue400: "rgb(96, 165, 250)",

  yellow500: "rgb(234, 179, 8)",
  yellow400: "rgb(250, 204, 21)",
  orange500: "rgb(249, 115, 22)",

  green500: "rgb(34, 197, 94)",
  green400: "rgb(74, 222, 128)",

  red500: "rgb(239, 68, 68)",
  red400: "rgb(248, 113, 113)",
  red600: "rgb(220, 38, 38)",
};

// No scaling — output at native 396x484
export const SCALE = 1;

export const WATCH_WIDTH = 396;
export const WATCH_HEIGHT = 484;

// WatchUiProfile — tuned for 396x484 native resolution
export const ui = {
  scoreValueSize: 48,
  teamNameSize: 16,
  inningSize: 17,
  inningHalfSize: 13,
  playerInfoSize: 16,
  countLabelSize: 14,
  countLabelWidth: 18,
  topPadding: 8,
  horizontalPadding: 16,
  bsoScoreSpacing: 16,
  bsoPlayerSpacing: 12,
  baseDiamondFrame: 50,
  baseCellSize: 17,
  countDotSize: 12,
};

export const teamThemes: Record<
  string,
  { primary: string; primaryDark: string; secondary: string; accent: string }
> = {
  DOOSAN: {
    primary: "rgb(19, 18, 48)",
    primaryDark: "rgb(10, 9, 24)",
    secondary: "rgb(239, 68, 68)",
    accent: "rgb(96, 165, 250)",
  },
  LG: {
    primary: "rgb(195, 4, 82)",
    primaryDark: "rgb(142, 2, 59)",
    secondary: "rgb(0,0,0)",
    accent: "rgb(244, 114, 182)",
  },
  KIWOOM: {
    primary: "rgb(130, 0, 36)",
    primaryDark: "rgb(92, 0, 26)",
    secondary: "rgb(212, 168, 67)",
    accent: "rgb(252, 165, 165)",
  },
  SAMSUNG: {
    primary: "rgb(7, 76, 161)",
    primaryDark: "rgb(5, 54, 120)",
    secondary: "rgb(255,255,255)",
    accent: "rgb(147, 197, 253)",
  },
  LOTTE: {
    primary: "rgb(4, 30, 66)",
    primaryDark: "rgb(2, 18, 48)",
    secondary: "rgb(227, 27, 35)",
    accent: "rgb(147, 197, 253)",
  },
  SSG: {
    primary: "rgb(206, 14, 45)",
    primaryDark: "rgb(150, 10, 32)",
    secondary: "rgb(255, 215, 0)",
    accent: "rgb(252, 165, 165)",
  },
  KT: {
    primary: "rgb(26, 26, 26)",
    primaryDark: "rgb(0,0,0)",
    secondary: "rgb(237, 28, 36)",
    accent: "rgb(163, 163, 163)",
  },
  HANWHA: {
    primary: "rgb(255, 102, 0)",
    primaryDark: "rgb(204, 82, 0)",
    secondary: "rgb(0,0,0)",
    accent: "rgb(253, 186, 116)",
  },
  KIA: {
    primary: "rgb(234, 0, 41)",
    primaryDark: "rgb(181, 0, 31)",
    secondary: "rgb(0,0,0)",
    accent: "rgb(252, 165, 165)",
  },
  NC: {
    primary: "rgb(49, 82, 136)",
    primaryDark: "rgb(33, 58, 97)",
    secondary: "rgb(207, 181, 59)",
    accent: "rgb(147, 197, 253)",
  },
};
