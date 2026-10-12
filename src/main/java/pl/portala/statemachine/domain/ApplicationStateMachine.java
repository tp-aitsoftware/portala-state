package pl.portala.statemachine.domain;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Hand-rolled state machine for the {@link Application} aggregate, each with an action
 * (merge the incoming data into the aggregate) and a guard (the AND-clauses of
 * the stage diagram).
 *
 * <pre>
 *   CREATED  --VERIFY--&gt;  VERIFIED  --ACCEPT--&gt;  ACCEPTED  --PUBLISH--&gt;  PUBLISHED (terminal)
 *      |                     |                       |
 *      +------DELETE---------+-------REJECT-----------+  --&gt;  DELETED (terminal, soft delete)
 * </pre>
 */
public final class ApplicationStateMachine {

    /** Action: merges the event payload into the aggregate before the guard runs. */
    private record Transition(
            ApplicationState from,
            ApplicationEvent event,
            ApplicationState to,
            BiFunction<Application, ApplicationChange, Application> action,
            Predicate<Application> guard,
            String guardDescription
    ) {
    }

    private final Map<Map.Entry<ApplicationState, ApplicationEvent>, Transition> transitions;

    public ApplicationStateMachine() {
        transitions = buildTable();
    }

    /**
     * Fires the event against the current snapshot.
     *
     * @throws IllegalStateTransitionException no such transition in the diagram 
     * @throws StateGuardViolationException    the transition exists but its guard fails
     */
    public Application fire(Application current, ApplicationEvent event, ApplicationChange change) {
        Transition transition = transitions.get(Map.entry(current.state(), event));
        if (transition == null) {
            throw new IllegalStateTransitionException(current.state(), event);
        }
        Application modified = transition.action().apply(current, change);
        if (!transition.guard().test(modified)) {
            throw new StateGuardViolationException(
                    "Guard '%s' failed for event %s from state %s".formatted(transition.guardDescription(), event, current.state()));
        }
        return modified.advancedTo(transition.to());
    }

    /** Events that the given state may fire - used to report legal events on errors. */
    public List<ApplicationEvent> permittedEvents(ApplicationState from) {
        return transitions.keySet().stream()
                .filter(key -> key.getKey() == from)
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
    }

    private static Map<Map.Entry<ApplicationState, ApplicationEvent>, Transition> buildTable() {
        List<Transition> table = List.of(
                new Transition(
                        ApplicationState.CREATED, ApplicationEvent.VERIFY, ApplicationState.VERIFIED,
                        (app, change) -> app.withAuthor(change.author()),
                        app -> app.createdDate() != null && app.reason() == null && Application.hasText(app.author()),
                        "createdDate exists, reason is null, author is set"),
                new Transition(
                        ApplicationState.VERIFIED, ApplicationEvent.ACCEPT, ApplicationState.ACCEPTED,
                        (app, change) -> app.withAuthor(change.author()),
                        app -> app.createdDate() != null && app.reason() == null && Application.hasText(app.author()),
                        "createdDate exists, reason is null, author is set"),
                new Transition(
                        ApplicationState.ACCEPTED, ApplicationEvent.PUBLISH, ApplicationState.PUBLISHED,
                        (app, change) -> app.withAuthor(change.author()).withPublicationId(change.publicationId()),
                        app -> app.objId() != null && app.createdDate() != null && app.reason() == null
                                && Application.hasText(app.author()) && app.publicationId() != null,
                        "unique objId and createdDate exist, reason is null, author is set, publicationId assigned"),
                new Transition(
                        ApplicationState.CREATED, ApplicationEvent.DELETE, ApplicationState.DELETED,
                        (app, change) -> app.withAuthor(change.author()).withReason(change.reason()),
                        app -> app.reason() != null && Application.hasText(app.author()),
                        "reason must be supplied on delete (F4), author is set"),
                new Transition(
                        ApplicationState.VERIFIED, ApplicationEvent.REJECT, ApplicationState.DELETED,
                        (app, change) -> app.withAuthor(change.author()).withReason(change.reason()),
                        app -> app.reason() != null && Application.hasText(app.author()),
                        "reason must be supplied on reject (F4), author is set"),
                new Transition(
                        ApplicationState.ACCEPTED, ApplicationEvent.REJECT, ApplicationState.DELETED,
                        (app, change) -> app.withAuthor(change.author()).withReason(change.reason()),
                        app -> app.reason() != null && Application.hasText(app.author()),
                        "reason must be supplied on reject (F4), author is set"));
        return table.stream().collect(Collectors.toMap(
                transition -> Map.entry(transition.from(), transition.event()),
                transition -> transition));
    }
}
