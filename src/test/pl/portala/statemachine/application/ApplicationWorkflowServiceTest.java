package pl.portala.statemachine.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.portala.statemachine.domain.Application;
import pl.portala.statemachine.domain.ApplicationEvent;
import pl.portala.statemachine.domain.ApplicationNotFoundException;
import pl.portala.statemachine.domain.ApplicationReason;
import pl.portala.statemachine.domain.ApplicationState;
import pl.portala.statemachine.domain.ApplicationStateMachine;
import pl.portala.statemachine.domain.ApplicationView;
import pl.portala.statemachine.domain.ContentNotEditableException;
import pl.portala.statemachine.domain.IllegalStateTransitionException;
import pl.portala.statemachine.domain.OptimisticLockConflictException;
import pl.portala.statemachine.domain.PageResult;
import pl.portala.statemachine.domain.StateGuardViolationException;
import pl.portala.statemachine.domain.UnknownApplicationEventException;
import pl.portala.statemachine.port.outgoing.ApplicationQueryRepository;
import pl.portala.statemachine.port.outgoing.ApplicationRepository;
import pl.portala.statemachine.port.outgoing.PublicationIdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The use-case service tested against <em>mocked</em> outgoing ports (
 * hexagonal, repository mocked for tests). Verifies orchestration: optimistic-lock
 * check, event parsing, publication-id assignment, append of the next version.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationWorkflowServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ApplicationRepository repository;

    @Mock
    private ApplicationQueryRepository queryRepository;

    @Mock
    private PublicationIdGenerator publicationIdGenerator;

    private ApplicationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationWorkflowService(repository, queryRepository, publicationIdGenerator, new ApplicationStateMachine(), CLOCK);
    }

    private static Application persisted(ApplicationState state, long version) {
        return Application.restore(100L, UUID.randomUUID(), LocalDateTime.now(CLOCK), version, state, "name", "body", "author", null, null);
    }

    /** append() echoes the aggregate back with an id, mimicking the JDBC adapter. */
    private void stubAppendEchoesInput() {
        when(repository.append(any(Application.class)))
                .thenAnswer(invocation -> ((Application) invocation.getArgument(0)).withId(999L));
    }

    @Test
    void createAppendsCreatedVersionZero() {
        stubAppendEchoesInput();
        ApplicationView view = service.create("name", "body", "author");

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(repository).append(captor.capture());
        assertThat(captor.getValue().state()).isEqualTo(ApplicationState.CREATED);
        assertThat(captor.getValue().version()).isZero();
        assertThat(view.state()).isEqualTo(ApplicationState.CREATED);
    }

    @Test
    void fireVerifyAppendsNextVersion() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.CREATED, 0), objId)));
        stubAppendEchoesInput();

        ApplicationView view = service.applyEvent(objId, "verify", "reviewer", null, 0);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(repository).append(captor.capture());
        assertThat(captor.getValue().state()).isEqualTo(ApplicationState.VERIFIED);
        assertThat(captor.getValue().version()).isEqualTo(1);
        assertThat(view.state()).isEqualTo(ApplicationState.VERIFIED);
    }

    @Test
    void publishTakesPublicationIdFromGenerator() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.ACCEPTED, 3), objId)));
        when(publicationIdGenerator.nextPublicationId()).thenReturn(500L);
        stubAppendEchoesInput();

        service.applyEvent(objId, "PUBLISH", "publisher", null, 3);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(repository).append(captor.capture());
        assertThat(captor.getValue().publicationId()).isEqualTo(500L);
        assertThat(captor.getValue().state()).isEqualTo(ApplicationState.PUBLISHED);
    }

    @Test
    void versionMismatchBlocksTheAppend() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.CREATED, 2), objId)));

        assertThatThrownBy(() -> service.applyEvent(objId, "VERIFY", "a", null, 0))
                .isInstanceOf(OptimisticLockConflictException.class);
        verify(repository, never()).append(any());
    }

    @Test
    void unknownEventIsRejectedBeforeAnyLoad() {
        assertThatThrownBy(() -> service.applyEvent(UUID.randomUUID(), "TELEPORT", "a", null, 0))
                .isInstanceOf(UnknownApplicationEventException.class);
    }

    @Test
    void illegalTransitionPropagates() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.CREATED, 0), objId)));

        assertThatThrownBy(() -> service.applyEvent(objId, "ACCEPT", "a", null, 0))
                .isInstanceOf(IllegalStateTransitionException.class);
        verify(repository, never()).append(any());
    }

    @Test
    void deleteWithoutReasonFailsGuard() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.CREATED, 0), objId)));

        assertThatThrownBy(() -> service.applyEvent(objId, "DELETE", "a", null, 0))
                .isInstanceOf(StateGuardViolationException.class);
    }

    @Test
    void editContentAllowedInCreated() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.CREATED, 0), objId)));
        stubAppendEchoesInput();

        service.editContent(objId, "new name", "new body", "editor", 0);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(repository).append(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("new name");
        assertThat(captor.getValue().state()).isEqualTo(ApplicationState.CREATED);
        assertThat(captor.getValue().version()).isEqualTo(1);
    }

    @Test
    void editContentBlockedInAccepted() {
        UUID objId = UUID.randomUUID();
        when(repository.findLatest(objId)).thenReturn(Optional.of(withObjId(persisted(ApplicationState.ACCEPTED, 2), objId)));

        assertThatThrownBy(() -> service.editContent(objId, "n", "b", "e", 2))
                .isInstanceOf(ContentNotEditableException.class);
        verify(repository, never()).append(any());
    }

    @Test
    void findMissingThrowsNotFound() {
        UUID objId = UUID.randomUUID();
        when(queryRepository.findLatest(objId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(objId)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void listDelegatesToQueryRepository() {
        PageResult<ApplicationView> empty = new PageResult<>(List.of(), 0, 10, 0, false);
        when(queryRepository.findCurrentVersions(eq("need"), eq(ApplicationState.CREATED), anyInt(), anyInt())).thenReturn(empty);

        PageResult<ApplicationView> result = service.list("need", "CREATED", 0, 10);

        assertThat(result.items()).isEmpty();
        verify(queryRepository).findCurrentVersions("need", ApplicationState.CREATED, 0, 10);
    }

    private static Application withObjId(Application base, UUID objId) {
        return Application.restore(base.id(), objId, base.createdDate(), base.version(), base.state(),
                base.name(), base.body(), base.author(), base.reason(), base.publicationId());
    }
}
