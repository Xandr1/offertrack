import { z } from "zod";
import { ApiError, NetworkError, ResponseValidationError } from "./errors";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export const request = async <T,>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestInit = {},
): Promise<T> => {
  let response: Response;

  try {
    response = await fetch(`${API_URL}${path}`, {
      ...options,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...(options.headers ?? {}),
      },
    });
  } catch {
    throw new NetworkError();
  }

  const text = await response.text();

  if (!response.ok) {
    throw new ApiError(response.status, text);
  }

  if (!text) {
    return undefined as T;
  }

  let json: unknown;

  try {
    json = JSON.parse(text);
  } catch {
    throw new ResponseValidationError();
  }

  const parsed = schema.safeParse(json);

  if (!parsed.success) {
    throw new ResponseValidationError();
  }

  return parsed.data;
};
