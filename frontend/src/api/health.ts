export type ServiceState = "UP" | "DOWN" | "UNAVAILABLE";

export async function checkReadiness(): Promise<ServiceState> {
  try {
    const response = await fetch("/api/health/readiness", {
      cache: "no-store",
      signal: AbortSignal.timeout(10000)
    });
    const data = await response.json();
    if (response.ok && data.status === "UP") return "UP";
    if (response.status === 503 && data.status === "DOWN") return "DOWN";
    return "UNAVAILABLE";
  } catch {
    return "UNAVAILABLE";
  }
}
