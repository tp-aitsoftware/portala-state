package pl.portala.statemachine.adapter.incoming.web.dto;

import jakarta.validation.constraints.NotBlank;

/** PUT /api/applications/{objId}/content body - edit; the version is the optimistic-lock token. */
public record EditContentRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "body is required") String body,
        String author,
        long expectedVersion
) {
}
