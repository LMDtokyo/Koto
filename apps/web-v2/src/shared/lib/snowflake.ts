/** Custom epoch: January 1, 2025 00:00:00 UTC — must match backend CUSTOM_EPOCH */
const EPOCH = 1_735_689_600_000n;

export function snowflakeToTimestamp(id: string): number {
  const snowflake = BigInt(id);
  return Number((snowflake >> 22n) + EPOCH);
}

export function snowflakeToDate(id: string): Date {
  return new Date(snowflakeToTimestamp(id));
}

export function compareSnowflakes(a: string, b: string): number {
  const diff = BigInt(a) - BigInt(b);
  if (diff < 0n) return -1;
  if (diff > 0n) return 1;
  return 0;
}
