export interface GameData {
  homeTeam: string;
  awayTeam: string;
  homeScore: number;
  awayScore: number;
  inning: string;
  ballCount: number;
  strikeCount: number;
  outCount: number;
  bases: { first: boolean; second: boolean; third: boolean };
  pitcher: string;
  batter: string;
}
