package pl.portala.statemachine.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.portala.statemachine.domain.Application;
import pl.portala.statemachine.domain.ApplicationChange;
import pl.portala.statemachine.domain.ApplicationEvent;
import pl.portala.statemachine.domain.ApplicationNotFoundException;
import pl.portala.statemachine.domain.ApplicationReason;
import pl.portala.statemachine.domain.ApplicationState;
import pl.portala.statemachine.domain.ApplicationStateMachine;
import pl.portala.statemachine.domain.ApplicationView;
import pl.portala.statemachine.domain.ContentNotEditableException;
import pl.portala.statemachine.domain.OptimisticLockConflictException;
import pl.portala.statemachine.domain.PageResult;
import pl.portala.statemachine.domain.UnknownApplicationEventException;
import pl.portala.statemachine.domain.UnknownApplicationStateException;
import pl.portala.statemachine.port.incoming.ApplicationUseCases;
import pl.portala.statemachine.port.outgoing.ApplicationQueryRepository;
import pl.portala.statemachine.port.outgoing.ApplicationRepository;
import pl.portala.statemachine.port.outgoing.PublicationIdGenerator;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Write-side use cases (driving-port implementation). It coordinates the
 * {@link ApplicationStateMachine}, the manual optimistic-locking check and the
 * append-only repository; it holds no SQL and no HTTP concerns.
 *
 * <p>Every successful call appends exactly one new row for the same objId with
 * {@code version + 1}, which is what builds the audit history.
 */
@Service
public class ApplicationWorkflowService implements ApplicationUseCases {

    private final ApplicationRepository repository;
    private final ApplicationQueryRepository queryRepository;
    private final PublicationIdGenerator publicationIdGenerator;
    private final ApplicationStateMachine machine;
    private final Clock clock;

    public ApplicationWorkflowService(
            ApplicationRepository repository,
            ApplicationQueryRepository queryRepository,
            PublicationIdGenerator publicationIdGenerator,
            ApplicationStateMachine machine,
            Clock clock
    ) {
        this.repository = repository;
        this.queryRepository = queryRepository;
        this.publicationIdGenerator = publicationIdGenerator;
        this.machine = machine;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApplicationView create(String name, String body, String author) {
        Application created = Application.start(UUID.randomUUID(), name, body, clock).withAuthorIfPresent(author);
        return ApplicationView.of(repository.append(created));
    }

    @Override
    @Transactional
    public ApplicationView editContent(UUID objId, String name, String body, String author, long expectedVersion) {
        Application current = loadAtExpectedVersion(objId, expectedVersion);
        if (!current.isContentEditable()) {
            throw new ContentNotEditableException(current.state());
        }
        Application edited = current.withContent(name, body).withAuthorIfPresent(author).advancedTo(current.state());
        return ApplicationView.of(repository.append(edited));
    }

    @Override
    @Transactional
    public ApplicationView applyEvent(UUID objId, String eventName, String author, ApplicationReason reason, long expectedVersion) {
        // parse first: an unknown event is a client error (400) and must not hit the DB
        ApplicationEvent event = parseEvent(eventName);
        Application current = loadAtExpectedVersion(objId, expectedVersion);
        // take the publication id only once we know the transition will really happen
        Long publicationId = event == ApplicationEvent.PUBLISH ? publicationIdGenerator.nextPublicationId() : null;
        Application next = machine.fire(current, event, new ApplicationChange(author, reason, publicationId));
        return ApplicationView.of(repository.append(next));
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationView find(UUID objId) {
        return queryRepository.findLatest(objId).orElseThrow(() -> new ApplicationNotFoundException(objId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationView> history(UUID objId) {
        List<Application> versions = repository.findAllVersions(objId);
        if (versions.isEmpty()) {
            throw new ApplicationNotFoundException(objId);
        }
        return versions.stream().map(ApplicationView::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ApplicationView> list(String nameFilter, String stateFilter, int page, int size) {
        return queryRepository.findCurrentVersions(nameFilter, parseState(stateFilter), page, size);
    }

    private Application loadAtExpectedVersion(UUID objId, long expectedVersion) {
        Application current = repository.findLatest(objId).orElseThrow(() -> new ApplicationNotFoundException(objId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockConflictException(objId, expectedVersion, current.version());
        }
        return current;
    }

    private static ApplicationEvent parseEvent(String eventName) {
        try {
            return ApplicationEvent.valueOf(eventName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownApplicationEventException(eventName);
        }
    }

    private static ApplicationState parseState(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return null;
        }
        try {
            return ApplicationState.valueOf(stateName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownApplicationStateException(stateName);
        }
    }
}
