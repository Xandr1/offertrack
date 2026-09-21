import { ApiError, getErrorMessage, getPasswordValidationMessage } from "./errors";

const validationError = (field: string, message: string, status = 400, code = "VALIDATION_ERROR") =>
  new ApiError(status, JSON.stringify({ code, fieldErrors: [{ field, message }] }));

describe("password validation wording", () => {
  it.each(["password", "newPassword"])("uses plain language for %s without changing the API response", field => {
    const size = validationError(field, "Password must be between 8 and 64 characters");
    expect(getPasswordValidationMessage(size, 7)).toBe("Password must be at least 8 characters.");
    expect(getPasswordValidationMessage(size, 65)).toBe("Password must be at most 64 characters.");
    const complexity = validationError(field, "Password must contain at least 1 lowercase letter, 1 uppercase letter and 1 digit");
    expect(getPasswordValidationMessage(complexity, 12)).toBe(
      "Password must include an uppercase letter, a lowercase letter, and a number.",
    );
    const bytes = validationError(field, "Password must be at most 72 UTF-8 bytes");
    expect(getPasswordValidationMessage(bytes, 41)).toBe("Password is too long.");
    expect(getErrorMessage(bytes)).toBe("Password is too long.");
    expect(JSON.parse(bytes.body).fieldErrors[0].message).toBe("Password must be at most 72 UTF-8 bytes");
  });

  it("preserves feedback for other fields", () => {
    const error = validationError("name", "Name must be at most 100 characters");
    expect(getPasswordValidationMessage(error, 12)).toBe("Name must be at most 100 characters");
  });

  it("leaves non-validation failures to the existing error handling", () => {
    expect(getPasswordValidationMessage(new Error("failure"), 12)).toBeNull();
    expect(getPasswordValidationMessage(new ApiError(400, "not json"), 12)).toBeNull();
    expect(getPasswordValidationMessage(validationError("password", "failure", 503), 12)).toBeNull();
    expect(getPasswordValidationMessage(validationError("password", "failure", 400, "INVALID_AUTH_TOKEN"), 12)).toBeNull();
  });
});
