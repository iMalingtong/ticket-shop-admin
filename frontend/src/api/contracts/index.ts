import type { components, operations } from "../generated/schema";

export type Schema<K extends keyof components["schemas"]> =
  components["schemas"][K];
export type ApiOperation = keyof operations;
export type AdminSummary = Schema<"AdminSummary">;
export type Permission = Schema<"Permission">;
export type ErrorCode = Schema<"ErrorCode">;
export type ApiEnvelope<T> = Readonly<{
  data: T;
  requestId: string;
  serverNow: string;
}>;
export type Page<T> = Readonly<{
  items: readonly T[];
  total: number;
  page: number;
  pageSize: 10 | 20 | 50 | 100;
}>;

const MAX_LONG = 9223372036854775807n;
export function id(value: string): string {
  if (!/^[1-9][0-9]{0,18}$/.test(value) || BigInt(value) > MAX_LONG)
    throw new Error("ID格式不正确");
  return value;
}
export function version(value: string): string {
  if (!/^(0|[1-9][0-9]{0,18})$/.test(value) || BigInt(value) > MAX_LONG)
    throw new Error("版本格式不正确");
  return value;
}
export function utcMicros(value: string): bigint {
  const match =
    /^([1-9]\d{3}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/.exec(value);
  const millis = Date.parse(value);
  if (
    !match ||
    !Number.isFinite(millis) ||
    new Date(millis).toISOString().slice(0, 19) !== match[1]
  )
    throw new Error("时间必须为有效UTC时刻");
  return (
    BigInt(millis) * 1000n + BigInt((match[2] ?? "").padEnd(6, "0").slice(3))
  );
}
export function requireValidity(from: string, until: string): void {
  if (utcMicros(from) >= utcMicros(until))
    throw new Error("生效时间必须早于到期时间");
}
export function requirePage(page: number, pageSize: number): void {
  if (
    !Number.isInteger(page) ||
    page < 1 ||
    page > 2147483647 ||
    ![10, 20, 50, 100].includes(pageSize)
  )
    throw new Error("分页参数不正确");
}

/** Convert the selected date to next local midnight, rather than adding 24 hours across DST. */
export function exclusiveDayEnd(date: string, timeZone: string): string {
  if (!/^[1-9]\d{3}-\d{2}-\d{2}$/.test(date)) throw new Error("日期格式不正确");
  const start = new Date(`${date}T00:00:00Z`);
  if (
    !Number.isFinite(start.getTime()) ||
    start.toISOString().slice(0, 10) !== date
  )
    throw new Error("日期无效");
  start.setUTCDate(start.getUTCDate() + 1);
  if (start.getUTCFullYear() > 9999) throw new Error("日期超出数据库范围");
  const anchor = start.getTime();
  const format = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  });
  const wallTime = (value: number) => {
    const parts = Object.fromEntries(
      format.formatToParts(new Date(value)).map(part => [part.type, part.value])
    );
    return Date.UTC(
      Number(parts.year),
      Number(parts.month) - 1,
      Number(parts.day),
      Number(parts.hour),
      Number(parts.minute),
      Number(parts.second)
    );
  };
  const matches = new Set<number>();
  for (let hours = -36; hours <= 36; hours += 6) {
    const sample = anchor + hours * 3600000;
    const candidate = anchor - (wallTime(sample) - sample);
    if (wallTime(candidate) === anchor) matches.add(candidate);
  }
  if (!matches.size) throw new Error("该时区下一日零点不存在，请选择明确时刻");
  return new Date(Math.min(...matches)).toISOString();
}

/** A dialog owns this instance. Closing it drops the only retained copy; never use a persisted store. */
export class OneTimeSecret {
  #value: string | undefined;
  show(value: string): void {
    this.#value = value;
  }
  read(): string | undefined {
    return this.#value;
  }
  close(): void {
    this.#value = undefined;
  }
}
export type MutationDialogState<T> =
  | { phase: "DRAFT"; input: T; expectedVersion: string }
  | {
      phase: "SUBMITTING";
      input: T;
      expectedVersion: string;
      idempotencyKey: string;
    }
  | {
      phase: "CONFLICT";
      input: T;
      expectedVersion: string;
      currentVersion?: string;
    }
  | {
      phase: "COMMITTED";
      resourceId: string;
      cacheSyncStatus: "SYNCED" | "PENDING";
      secretAvailable: boolean;
    };
