package pl.portala.statemachine.adapter.incoming.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/applications body - requires a name and a body; author is optional at creation. */
public record CreateApplicationRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "body is required") String body,
        String author
) {
}
