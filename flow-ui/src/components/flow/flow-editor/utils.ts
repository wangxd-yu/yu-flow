export function unwrapDebugResult(result: any, fallbackMsg: string) {
  if (result?.code === 0 && result.data) return result.data;
  if (result?.data) return result.data;
  if (result?.traceId || result?.sessionId || result?.stepLogs) return result;
  throw new Error(result?.msg || fallbackMsg);
}
