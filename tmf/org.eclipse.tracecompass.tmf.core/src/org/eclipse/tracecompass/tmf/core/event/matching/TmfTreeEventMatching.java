package org.eclipse.tracecompass.tmf.core.event.matching;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventDependency.DependencyEvent;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventMatching.Direction;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest;
import org.eclipse.tracecompass.tmf.core.request.TmfEventRequest;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

/**
 * Class used to match tree-like events (event having parent event) in an trace
 *
 * @author Eya-Tom Augustin SANGAM
 */
public class TmfTreeEventMatching implements ITmfEventMatching {

    private static final Set<ITmfMatchEventDefinition> MATCH_DEFINITIONS = new HashSet<>();

    /**
     * The array of traces to match
     */
    private final @NonNull Collection<@NonNull ITmfTrace> fTraces;

    private final @NonNull Collection<@NonNull ITmfTrace> fIndividualTraces;
    /**
     * The class to call once a match is found
     */
    private final IMatchProcessingUnit fMatches;

    private final Multimap<ITmfTrace, ITmfMatchEventDefinition> fMatchMap = HashMultimap.create();

    /**
     * Hashtables for all events
     */
    private final Map<IEventMatchingKey, DependencyEvent> fAllEvents = new HashMap<>();

    /**
     * Multimap for unmatched events The key is the key describing the event we
     * needed to be present in order to process the key which is in the pair.
     */
    private final Multimap<IEventMatchingKey, Triple<IEventMatchingKey, DependencyEvent, Direction>> fUnmatchedEvents = ArrayListMultimap.create();

    /**
     * Constructor with multiple traces
     *
     * @param traces
     *            The set of traces for which to match events
     * @since 1.0
     */
    public TmfTreeEventMatching(Collection<@NonNull ITmfTrace> traces) {
        this(traces, new TmfEventMatches());
    }

    /**
     * Constructor with multiple traces and a match processing object
     *
     * @param traces
     *            The set of traces for which to match events
     * @param tmfEventMatches
     *            The match processing class
     */
    public TmfTreeEventMatching(Collection<@NonNull ITmfTrace> traces, IMatchProcessingUnit tmfEventMatches) {
        if (tmfEventMatches == null) {
            throw new IllegalArgumentException();
        }
        fTraces = new HashSet<>(traces);
        fMatches = tmfEventMatches;
        Set<@NonNull ITmfTrace> individualTraces = new HashSet<>();
        for (ITmfTrace trace : traces) {
            individualTraces.addAll(TmfTraceManager.getTraceSet(trace));
        }
        fIndividualTraces = individualTraces;
        individualTraces.stream()
                .map(ITmfTrace::getHostId)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the traces to synchronize. These are the traces that were
     * specified in the constructor, they may contain either traces or
     * experiment.
     *
     * @return The traces to synchronize
     */
    protected Collection<ITmfTrace> getTraces() {
        return new HashSet<>(fTraces);
    }

    /**
     * Returns the individual traces to process. If some of the traces specified
     * to synchronize in the constructor were experiments, only the traces
     * contained in this experiment will be returned. No {@link TmfExperiment}
     * are returned by this method.
     *
     * @return The individual traces to synchronize, no experiments
     */
    protected Collection<ITmfTrace> getIndividualTraces() {
        return fIndividualTraces;
    }

    /**
     * Returns the match processing unit
     *
     * @return The match processing unit
     */
    protected IMatchProcessingUnit getProcessingUnit() {
        return fMatches;
    }

    /**
     * Returns the match event definitions corresponding to the trace
     *
     * @param trace
     *            The trace
     * @return The match event definition object
     */
    protected Collection<ITmfMatchEventDefinition> getEventDefinitions(ITmfTrace trace) {
        return ImmutableList.copyOf(fMatchMap.get(trace));
    }

    /**
     * Method that initializes any data structure for the event matching. It
     * also assigns to each trace an event matching definition instance that
     * applies to the trace
     *
     * @since 1.0
     */
    public void initMatching() {
        // Initialize the matching infrastructure (unmatched event lists)
        fAllEvents.clear();
        fUnmatchedEvents.clear();

        fMatches.init(fTraces);
        for (ITmfTrace trace : getIndividualTraces()) {
            for (ITmfMatchEventDefinition def : MATCH_DEFINITIONS) {
                if (def.canMatchTrace(trace)) {
                    fMatchMap.put(trace, def);
                }
            }
        }
    }

    /**
     * Calls any post matching methods of the processing class
     */
    protected void finalizeMatching() {
        System.out.println(fUnmatchedEvents.size());
        fMatches.matchingEnded();
    }

    /**
     * Prints stats from the matching
     *
     * @return string of statistics
     */
    @Override
    public String toString() {
        final String cr = System.getProperty("line.separator"); //$NON-NLS-1$
        StringBuilder b = new StringBuilder();
        b.append(getProcessingUnit());
        b.append("All traces:" + cr + //$NON-NLS-1$ //$NON-NLS-2$
                "  " + fAllEvents.size() + " all events" + cr + //$NON-NLS-1$ //$NON-NLS-2$
                "  " + fUnmatchedEvents.size() + " unmatched events" + cr); //$NON-NLS-1$ //$NON-NLS-2$
        return b.toString();
    }

    /**
     * Matches one event
     *
     * @param event
     *            The event to match
     * @param trace
     *            The trace to which this event belongs
     * @param monitor
     *            The monitor for the synchronization job
     * @since 1.0
     */
    public void matchEvent(ITmfEvent event, ITmfTrace trace, @NonNull IProgressMonitor monitor) {
        System.out.println("--------------------------------------------------------");
        System.out.println(event);
        ITmfTreeMatchEventDefinition def = null;
        IEventMatchingKey key = null;
        for (ITmfMatchEventDefinition oneDef : getEventDefinitions(event.getTrace())) {
            if (!(oneDef instanceof ITmfTreeMatchEventDefinition)) {
                continue;
            }
            def = (ITmfTreeMatchEventDefinition) oneDef;
            key = def.getEventKey(event);
            if (key != null) {
                break;
            }
        }

        if (def == null || key == null) {
            return;
        }

        DependencyEvent depEvent = new DependencyEvent(event);
        fAllEvents.put(key, depEvent);

        IEventMatchingKey parentKey = def.getParentEventKey(event);
        if (parentKey != null) {
            // Search for the parent event in the table
            Direction direction = def.getDirection(event);
            if (fAllEvents.containsKey(parentKey)) {
                DependencyEvent parentDepEvent = fAllEvents.get(parentKey);
                // TODO : Find the direction base on the event type
                TmfEventDependency dep = null;
                switch (direction) {
                case EFFECT:
                    dep = new TmfEventDependency(parentDepEvent, depEvent);
                    break;
                case CAUSE:
                    dep = new TmfEventDependency(depEvent, parentDepEvent);
                    break;
                default:
                    break;
                }
                if (dep != null) {
                    getProcessingUnit().addMatch(key, dep);
                    monitor.subTask(NLS.bind(Messages.TmfEventMatching_MatchesFound, getProcessingUnit().countMatches()));
                }
            } else {
                // The parent has not been seen yet. We add the event in the
                // unmatched map
                fUnmatchedEvents.put(parentKey, Triple.of(key, depEvent, direction));
            }
        }

        // We now need to process all events that couldn't be process because
        // they required this one
        Collection<Triple<IEventMatchingKey, DependencyEvent, Direction>> dependentEvents = fUnmatchedEvents.get(key);
        for (Triple<IEventMatchingKey, DependencyEvent, Direction> dependentEvent : dependentEvents) {
            IEventMatchingKey childEventKey = dependentEvent.getLeft();
            DependencyEvent childDepEvent = dependentEvent.getMiddle();
            Direction direction = dependentEvent.getRight();
            TmfEventDependency dep = null;
            switch (direction) {
            case EFFECT:
                dep = new TmfEventDependency(depEvent, childDepEvent);
                break;
            case CAUSE:
                dep = new TmfEventDependency(childDepEvent, depEvent);
                break;
            default:
                break;
            }
            if (dep != null) {
                getProcessingUnit().addMatch(childEventKey, dep);
                monitor.subTask(NLS.bind(Messages.TmfEventMatching_MatchesFound, getProcessingUnit().countMatches()));
            }
        }
        fUnmatchedEvents.removeAll(key);

    }

    /**
     * Method that start the process of matching events
     *
     * @return Whether the match was completed correctly or not
     */
    @Override
    public boolean matchEvents() {

        /* Are there traces to match? If no, return false */
        if (fTraces.isEmpty()) {
            return false;
        }

        initMatching();

        /*
         * Actual analysis will be run on a separate thread
         */
        Job job = new Job(Messages.TmfEventMatching_MatchingEvents) {
            @Override
            protected IStatus run(final IProgressMonitor monitor) {
                /**
                 * FIXME For now, we use the experiment strategy: the trace that
                 * is asked to be matched is actually an experiment and the
                 * experiment does the request. But depending on how divergent
                 * the traces' times are and how long it takes to get the first
                 * match, it can use a lot of memory.
                 *
                 * Some strategies can help limit the memory usage of this
                 * algorithm:
                 *
                 * <pre>
                 * Other possible matching strategy:
                 * * start with the shortest trace
                 * * take a few events at the beginning and at the end and try
                 *   to match them
                 * </pre>
                 */
                for (ITmfTrace trace : fTraces) {
                    monitor.beginTask(NLS.bind(Messages.TmfEventMatching_LookingEventsFrom, trace.getName()), IProgressMonitor.UNKNOWN);
                    setName(NLS.bind(Messages.TmfEventMatching_RequestingEventsFrom, trace.getName()));

                    /* Send the request to the trace */
                    TreeEventMatchingBuildRequest request = new TreeEventMatchingBuildRequest(TmfTreeEventMatching.this, trace, monitor);
                    trace.sendRequest(request);
                    try {
                        request.waitForCompletion();
                    } catch (InterruptedException e) {
                        Activator.logInfo(e.getMessage());
                    }
                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }
                }
                return Status.OK_STATUS;
            }
        };
        job.schedule();
        try {
            job.join();
        } catch (InterruptedException e) {

        }

        finalizeMatching();

        return true;
    }

    /**
     * Registers an event match definition
     *
     * @param match
     *            The event matching definition
     */
    public static void registerMatchObject(ITmfTreeMatchEventDefinition match) {
        MATCH_DEFINITIONS.add(match);
    }

}

class TreeEventMatchingBuildRequest extends TmfEventRequest {

    private final TmfTreeEventMatching matching;
    private final ITmfTrace trace;
    private final @NonNull IProgressMonitor fMonitor;

    TreeEventMatchingBuildRequest(TmfTreeEventMatching matching, ITmfTrace trace, IProgressMonitor monitor) {
        super(ITmfEvent.class,
                TmfTimeRange.ETERNITY,
                0,
                ITmfEventRequest.ALL_DATA,
                ITmfEventRequest.ExecutionType.FOREGROUND);
        this.matching = matching;
        this.trace = trace;
        if (monitor == null) {
            fMonitor = new NullProgressMonitor();
        } else {
            fMonitor = monitor;
        }
    }

    @Override
    public void handleData(final ITmfEvent event) {
        super.handleData(event);
        if (fMonitor.isCanceled()) {
            this.cancel();
        }
        matching.matchEvent(event, trace, fMonitor);
    }
}
