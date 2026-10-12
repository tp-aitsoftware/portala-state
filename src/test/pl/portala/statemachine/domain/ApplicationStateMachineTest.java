package pl.portala.statemachine.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An unit tests for the states, transitions, guards and actions of the
 * {@link ApplicationStateMachine}. No Spring, no database: the machine is pure
 * domain logic.
 */
class ApplicationStateMachineTest {

    private final ApplicationStateMachine machine = new ApplicationStateMachine();

    private static Application snapshot(ApplicationState state) {
        return Application.restore(1L, UUID.randomUUID(), LocalDateTime.now(), 0L,
                state, "some name", "some body", null, null, null);
    }

    @Test
    @DisplayName("drop NEW: a fresh request is born in CREATED with version 0")
    void startIsCreated() {
        Application created = Application.start(UUID.randomUUID(), "name", "body", java.time.Clock.systemUTC());
        assertThat(created.state()).isEqualTo(ApplicationState.CREATED);
        assertThat(created.version()).isZero();
    }

    @Test
    @DisplayName("creation requires a name and a body")
    void startRequiresNameAndBody() {
        assertThatThrownBy(() -> Application.start(UUID.randomUUID(), " ", "body", java.time.Clock.systemUTC()))
                .isInstanceOf(StateGuardViolationException.class);
        assertThatThrownBy(() -> Application.start(UUID.randomUUID(), "name", null, java.time.Clock.systemUTC()))
                .isInstanceOf(StateGuardViolationException.class);
    }

    @Nested
    class HappyPath {

        @Test
        void verifyFromCreated() {
            Application next = machine.fire(snapshot(ApplicationState.CREATED), ApplicationEvent.VERIFY,
                    new ApplicationChange("author-1", null, null));
            assertThat(next.state()).isEqualTo(ApplicationState.VERIFIED);
            assertThat(next.version()).isEqualTo(1L);
            assertThat(next.author()).isEqualTo("author-1");
        }

        @Test
        void acceptFromVerified() {
            Application next = machine.fire(snapshot(ApplicationState.VERIFIED), ApplicationEvent.ACCEPT,
                    new ApplicationChange("author-1", null, null));
            assertThat(next.state()).isEqualTo(ApplicationState.ACCEPTED);
        }

        @Test
        void publishFromAcceptedAssignsPublicationId() {
            Application next = machine.fire(snapshot(ApplicationState.ACCEPTED), ApplicationEvent.PUBLISH,
                    new ApplicationChange("author-1", null, 42L));
            assertThat(next.state()).isEqualTo(ApplicationState.PUBLISHED);
            assertThat(next.publicationId()).isEqualTo(42L);
        }

        @Test
        void fullLifecycleIncrementsVersionEveryStep() {
            Application app = Application.start(UUID.randomUUID(), "n", "b", java.time.Clock.systemUTC());
            app = machine.fire(app, ApplicationEvent.VERIFY, new ApplicationChange("a", null, null));
            app = machine.fire(app, ApplicationEvent.ACCEPT, new ApplicationChange("a", null, null));
            app = machine.fire(app, ApplicationEvent.PUBLISH, new ApplicationChange("a", null, 7L));
            assertThat(app.state()).isEqualTo(ApplicationState.PUBLISHED);
            assertThat(app.version()).isEqualTo(3L);
            assertThat(app.publicationId()).isEqualTo(7L);
        }
    }

    @Nested
    class DeleteAndReject {

        @Test
        void deleteFromCreatedNeedsReason() {
            Application next = machine.fire(snapshot(ApplicationState.CREATED), ApplicationEvent.DELETE,
                    new ApplicationChange("a", ApplicationReason.FINISH_ON_DELETE, null));
            assertThat(next.state()).isEqualTo(ApplicationState.DELETED);
            assertThat(next.reason()).isEqualTo(ApplicationReason.FINISH_ON_DELETE);
        }

        @Test
        void rejectFromVerifiedAndAccepted() {
            Application fromVerified = machine.fire(snapshot(ApplicationState.VERIFIED), ApplicationEvent.REJECT,
                    new ApplicationChange("a", ApplicationReason.FINISH_ON_REJECTED, null));
            Application fromAccepted = machine.fire(snapshot(ApplicationState.ACCEPTED), ApplicationEvent.REJECT,
                    new ApplicationChange("a", ApplicationReason.FINISH_ON_REJECTED, null));
            assertThat(fromVerified.state()).isEqualTo(ApplicationState.DELETED);
            assertThat(fromAccepted.state()).isEqualTo(ApplicationState.DELETED);
        }

        @Test
        @DisplayName("delete/reject without a reason is rejected by the guard")
        void rejectWithoutReasonFailsGuard() {
            assertThatThrownBy(() -> machine.fire(snapshot(ApplicationState.VERIFIED), ApplicationEvent.REJECT,
                    new ApplicationChange("a", null, null)))
                    .isInstanceOf(StateGuardViolationException.class);
        }
    }

    @Nested
    class Guards {

        @Test
        @DisplayName("VERIFY/ACCEPT/PUBLISH require an author")
        void authorRequiredOnForwardTransitions() {
            assertThatThrownBy(() -> machine.fire(snapshot(ApplicationState.CREATED), ApplicationEvent.VERIFY,
                    ApplicationChange.NONE))
                    .isInstanceOf(StateGuardViolationException.class);
        }

        @Test
        @DisplayName("PUBLISH requires a publicationId")
        void publishRequiresPublicationId() {
            assertThatThrownBy(() -> machine.fire(snapshot(ApplicationState.ACCEPTED), ApplicationEvent.PUBLISH,
                    new ApplicationChange("a", null, null)))
                    .isInstanceOf(StateGuardViolationException.class);
        }
    }

    @Nested
    class IllegalTransitions {

        @Test
        @DisplayName("an event with no matching edge in the diagram is a conflict")
        void unknownEdgeIsIllegal() {
            // ACCEPT is only legal from VERIFIED, not from CREATED
            assertThatThrownBy(() -> machine.fire(snapshot(ApplicationState.CREATED), ApplicationEvent.ACCEPT,
                    new ApplicationChange("a", null, null)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("terminal states accept no event")
        void terminalStatesAreFrozen() {
            for (ApplicationEvent event : ApplicationEvent.values()) {
                if (event == ApplicationEvent.CREATE) {
                    continue;
                }
                assertThatThrownBy(() -> machine.fire(snapshot(ApplicationState.PUBLISHED), event,
                        new ApplicationChange("a", ApplicationReason.FINISH_ON_DELETE, 1L)))
                        .as("PUBLISHED must not fire %s", event)
                        .isInstanceOf(IllegalStateTransitionException.class);
                assertThatThrownBy(() -> machine.fire(snapshot(ApplicationState.DELETED), event,
                        new ApplicationChange("a", ApplicationReason.FINISH_ON_DELETE, 1L)))
                        .as("DELETED must not fire %s", event)
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }

        @Test
        void permittedEventsMatchTheDiagram() {
            assertThat(machine.permittedEvents(ApplicationState.CREATED)).containsExactlyInAnyOrder(ApplicationEvent.VERIFY, ApplicationEvent.DELETE);
            assertThat(machine.permittedEvents(ApplicationState.VERIFIED)).containsExactlyInAnyOrder(ApplicationEvent.ACCEPT, ApplicationEvent.REJECT);
            assertThat(machine.permittedEvents(ApplicationState.ACCEPTED)).containsExactlyInAnyOrder(ApplicationEvent.PUBLISH, ApplicationEvent.REJECT);
            assertThat(machine.permittedEvents(ApplicationState.PUBLISHED)).isEmpty();
            assertThat(machine.permittedEvents(ApplicationState.DELETED)).isEmpty();
        }
    }
}
