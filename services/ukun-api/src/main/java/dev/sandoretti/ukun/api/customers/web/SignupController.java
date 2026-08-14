package dev.sandoretti.ukun.api.customers.web;

import dev.sandoretti.ukun.api.customers.application.SignupRequest;
import dev.sandoretti.ukun.api.customers.application.SignupService;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/signup} (RF-06). Public, and it has to be: signing up cannot require being signed
 * in.
 *
 * <p>The saga is orchestrated a layer further in — {@link SignupService} owns the customer's half of
 * it, {@code AccountRegistrationService} the ordering the employee's alta shares — and this class does
 * the one thing a controller should, which is turn the outcome into the status the contract declares.
 * Both outcomes are successes and the difference between them is real: {@code 201} when the account is
 * active and the person can log in,
 * {@code 202} when the profile is written but the activation has not been confirmed and repeating the
 * signup will finish it.
 *
 * <p>Neither answer has a body. The contract gives these two responses no content, and there is
 * nothing useful to return: the customer's own data comes from {@code /api/me/profile} once they have
 * a token, and echoing an id to an anonymous caller would hand out an identifier before there is
 * anybody entitled to it.
 *
 * <p>The method name is the {@code operationId} springdoc publishes, so it is {@code signup} — the
 * name the frozen contract uses and the one the SPA's generated client is named after.
 */
@RestController
@RequestMapping(path = "/api/signup", produces = MediaType.APPLICATION_JSON_VALUE)
class SignupController {

    private final SignupService signups;

    SignupController(SignupService signups) {
        this.signups = signups;
    }

    /**
     * {@code @SecurityRequirements} with no entries is how {@code security: []} is spelled for
     * springdoc — the contract marks this operation public, and without the annotation the generated
     * document would inherit the service's default bearer requirement and the drift check would fail
     * on a difference that is not in the code's behaviour.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @ApiResponse(
            responseCode = "201",
            description = "Customer created and account activated.",
            content = @Content)
    @ApiResponse(
            responseCode = "202",
            description = "Customer created, activation not yet confirmed.",
            content = @Content)
    @BadRequestResponse
    @ApiResponse(
            responseCode = "409",
            description = "`EMAIL_ALREADY_EXISTS`, propagated from ukun-auth.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @InternalErrorResponse
    ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        return switch (signups.signUp(request)) {
            case COMPLETED -> ResponseEntity.status(HttpStatus.CREATED).build();
            case ACTIVATION_PENDING -> ResponseEntity.accepted().build();
        };
    }
}
