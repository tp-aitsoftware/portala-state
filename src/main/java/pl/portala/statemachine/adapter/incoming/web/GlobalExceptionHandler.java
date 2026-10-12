package pl.portala.statemachine.adapter.incoming.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.portala.statemachine.domain.ApplicationNotFoundException;
import pl.portala.statemachine.domain.ContentNotEditableException;
import pl.portala.statemachine.domain.IllegalStateTransitionException;
import pl.portala.statemachine.domain.OptimisticLockConflictException;
import pl.portala.statemachine.domain.StateGuardViolationException;
import pl.portala.statemachine.domain.UnknownApplicationEventException;
import pl.portala.statemachine.domain.UnknownApplicationStateException;

import java.util.stream.Collectors;

/**
 * Global error handler (R6). Maps the domain exceptions to stable HTTP status codes
 * as RFC-7807 Problem Details, so clients can branch on the status:
 *
 * <ul>
 *   <li>400 - malformed request / unknown event or state / bean-validation failure</li>
 *   <li>404 - unknown objId</li>
 *   <li>409 - the request conflicts with the current state: transition not permitted
 *       by the diagram (F1), or a guard / content rule failed (F3, F4, F5) - see the
 *       {@code code} property to tell them apart</li>
 *   <li>412 - optimistic-lock version mismatch</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ProblemDetail notFound(ApplicationNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ProblemDetail illegalTransition(IllegalStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "ILLEGAL_STATE_TRANSITION", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ProblemDetail lockConflict(OptimisticLockConflictException e) {
        ProblemDetail detail = problem(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT", e.getMessage());
        detail.setProperty("expectedVersion", e.expectedVersion());
        detail.setProperty("currentVersion", e.currentVersion());
        return detail;
    }

    @ExceptionHandler({StateGuardViolationException.class, ContentNotEditableException.class})
    public ProblemDetail guardViolation(RuntimeException e) {
        // House convention (ECM): business-rule violations are 409 Conflict, distinguished
        // by the machine-readable 'code' rather than by an exotic status.
        return problem(HttpStatus.CONFLICT, "GUARD_VIOLATION", e.getMessage());
    }

    @ExceptionHandler({UnknownApplicationEventException.class, UnknownApplicationStateException.class})
    public ProblemDetail unknownEnum(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(code);
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
