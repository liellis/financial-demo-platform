export type Row = Record<string, any>;
export async function request(
  path: string,
  token: string,
  method = "GET",
  body?: unknown,
): Promise<any> {
  const form = body instanceof FormData;
  const result = await fetch("/api/v2" + path, {
    method,
    headers: {
      Authorization: "Bearer " + token,
      ...(!form && body ? { "Content-Type": "application/json" } : {}),
    },
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
  });
  const data = await result.json();
  if (!result.ok) {
    const error = new Error(data.code || "HTTP_" + result.status) as Error & {
      trace?: string;
      params?: Row;
    };
    error.trace = data.trace_id;
    error.params = data.params;
    throw error;
  }
  return data;
}
