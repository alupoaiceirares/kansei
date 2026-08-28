// we have two error shapes: domain exceptions (EmailAlreadyExists, InvalidCredentials, InvalidCurrentPassword, ...) return {message}; @Valid/bean-validation failures on a DTO return {errors: {field: message}} instead, with no top-level message at all. 
// A frontend that only reads body.message silently loses every bean-validation reason (change password, no eerors, redirect)
export type ApiErrorBody = { message?: string; errors?: Record<string, string> } | null | undefined;

/** One-line summary for the page-level Banner. */
export function extractErrorMessage(body: ApiErrorBody, fallback: string): string {
  if (body?.message) return body.message;
  if (body?.errors) {
    const values = Object.values(body.errors);
    if (values.length) return values.join(" ");
  }
  return fallback;
}

// Domain exceptions have no field key (they're a plain {message}), but a few of them are unambiguously about one specific field, infer it from the exact wording so that field's input can still get the red outline. 
// Coupled to shieldwall's exact exception messages (EmailAlreadyExistsException, UsernameAlreadyExistsException, InvalidCurrentPasswordException)... update both sides together if either changes
function inferFieldFromMessage(message: string): string | null {
  if (message.startsWith("Email already in use")) return "email";
  if (message.startsWith("Username already taken")) return "username";
  if (message === "Current password is incorrect") return "currentPassword";
  return null;
}

/** Field name -> message, for red-outlining the specific input(s) that were wrong */
export function extractFieldErrors(body: ApiErrorBody): Record<string, string> {
  if (body?.errors) return body.errors;
  if (body?.message) {
    const field = inferFieldFromMessage(body.message);
    if (field) return { [field]: body.message };
  }
  return {};
}
