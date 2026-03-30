// iPhone 6.7" App Store screenshot size
export const IPHONE_WIDTH = 1290;
export const IPHONE_HEIGHT = 2796;

// AppColors from Colors.swift
export const appColors = {
  gray950: "#0A0A0B",
  gray900: "#18181B",
  gray800: "#27272A",
  gray700: "#3F3F46",
  gray600: "#52525B",
  gray500: "#71717A",
  gray400: "#A1A1AA",
  gray300: "#D4D4D8",
  gray200: "#E4E4E7",
  gray100: "#F4F4F5",
  blue500: "#3B82F6",
  blue600: "#2563EB",
  blue700: "#1D4ED8",
  blue400: "#60A5FA",
  blue200: "#BFDBFE",
  yellow500: "#EAB308",
  yellow400: "#FACC15",
  orange500: "#F97316",
  green500: "#22C55E",
  green400: "#4ADE80",
  red500: "#EF4444",
  red400: "#F87171",
};

export interface TeamDef {
  name: string;
  nameKo: string;
  primary: string;
  primaryDark: string;
  secondary: string;
  logo: string; // filename in public/
}

export const teams: TeamDef[] = [
  { name: "DOOSAN", nameKo: "두산 베어스", primary: "#131230", primaryDark: "#0A0918", secondary: "#EF4444", logo: "team_doosan.png" },
  { name: "LG", nameKo: "LG 트윈스", primary: "#C30452", primaryDark: "#8E023B", secondary: "#000000", logo: "team_lg.png" },
  { name: "KIWOOM", nameKo: "키움 히어로즈", primary: "#820024", primaryDark: "#5C001A", secondary: "#D4A843", logo: "team_kiwoom.png" },
  { name: "SAMSUNG", nameKo: "삼성 라이온즈", primary: "#074CA1", primaryDark: "#053678", secondary: "#FFFFFF", logo: "team_samsung.png" },
  { name: "LOTTE", nameKo: "롯데 자이언츠", primary: "#041E42", primaryDark: "#021230", secondary: "#E31B23", logo: "team_lotte.png" },
  { name: "SSG", nameKo: "SSG 랜더스", primary: "#CE0E2D", primaryDark: "#960A20", secondary: "#FFD700", logo: "team_ssg.png" },
  { name: "KT", nameKo: "KT 위즈", primary: "#1A1A1A", primaryDark: "#000000", secondary: "#ED1C24", logo: "team_kt.png" },
  { name: "HANWHA", nameKo: "한화 이글스", primary: "#FF6600", primaryDark: "#CC5200", secondary: "#000000", logo: "team_hanwha.png" },
  { name: "KIA", nameKo: "KIA 타이거즈", primary: "#EA0029", primaryDark: "#B5001F", secondary: "#000000", logo: "team_kia.png" },
  { name: "NC", nameKo: "NC 다이노스", primary: "#315288", primaryDark: "#213A61", secondary: "#CFB53B", logo: "team_nc.png" },
];

// 선택된 팀 (스크린샷 기준)
export const myTeam = teams.find((t) => t.name === "SSG")!;
