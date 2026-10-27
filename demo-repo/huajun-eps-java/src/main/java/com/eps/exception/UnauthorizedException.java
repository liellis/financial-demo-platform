package com.eps.exception;

/**
 * Exception thrown when a user attempts an action they are not
 * authorized to perform, such as approving their own payment
 * or accessing a restricted function.
 *
 * <p>This maps to HTTP 403 (Forbidden) via
 * {@link GlobalExceptionHandler}.</p>
 */
public class UnauthorizedException extends RuntimeException {

    /** The user ID who attempted the unauthorized action. */
    private final String userId;

    /** The action that was attempted. */
    private final String action;

    /**
     * Constructs a new exception with a descriptive message.
     *
     * @param message the error description
     */
    public UnauthorizedException(String message) {
        super(message);
        this.userId = null;
        this.action = null;
    }

    /**
     * Constructs a new exception specifying the user and the action attempted.
     *
     * @param userId the user ID who attempted the action
     * @param action the action that was attempted (e.g. "APPROVE", "DELETE")
     */
    public UnauthorizedException(String userId, String action) {
        super("User [" + userId + "] is not authorized to perform action: " + action);
        this.userId = userId;
        this.action = action;
    }

    /**
     * Returns the user ID who attempted the unauthorized action,
     * or null if not specified.
     *
     * @return the user ID, or null
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns the action that was attempted, or null if not specified.
     *
     * @return the action name, or null
     */
    public String getAction() {
        return action;
    }
}
