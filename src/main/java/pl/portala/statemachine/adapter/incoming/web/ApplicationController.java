package pl.portala.statemachine.adapter.incoming.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.portala.statemachine.adapter.incoming.web.dto.ApplicationResponse;
import pl.portala.statemachine.adapter.incoming.web.dto.CreateApplicationRequest;
import pl.portala.statemachine.adapter.incoming.web.dto.EditContentRequest;
import pl.portala.statemachine.adapter.incoming.web.dto.PageResponse;
import pl.portala.statemachine.adapter.incoming.web.dto.TransitionRequest;
import pl.portala.statemachine.domain.ApplicationView;
import pl.portala.statemachine.port.incoming.ApplicationUseCases;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ApplicationUseCases useCases;

    public ApplicationController(ApplicationUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationView view = useCases.create(request.name(), request.body(), request.author());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(view));
    }

    @GetMapping("/{objId}")
    public ApplicationResponse find(@PathVariable UUID objId) {
        return ApplicationResponse.from(useCases.find(objId));
    }

    @GetMapping("/{objId}/history")
    public List<ApplicationResponse> history(@PathVariable UUID objId) {
        return useCases.history(objId).stream().map(ApplicationResponse::from).toList();
    }

    @PutMapping("/{objId}/content")
    public ApplicationResponse editContent(@PathVariable UUID objId, @Valid @RequestBody EditContentRequest request) {
        ApplicationView view = useCases.editContent(objId, request.name(), request.body(), request.author(), request.expectedVersion());
        return ApplicationResponse.from(view);
    }

    @PostMapping("/{objId}/events")
    public ApplicationResponse fireEvent(@PathVariable UUID objId, @Valid @RequestBody TransitionRequest request) {
        ApplicationView view = useCases.applyEvent(objId, request.event(), request.author(), request.reason(), request.expectedVersion());
        return ApplicationResponse.from(view);
    }

    @GetMapping
    public PageResponse<ApplicationResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int effectiveSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        return PageResponse.from(useCases.list(name, state, Math.max(page, 0), effectiveSize), ApplicationResponse::from);
    }
}
