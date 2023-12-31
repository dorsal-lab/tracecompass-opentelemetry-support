/*******************************************************************************
 * Copyright (c) 2017 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.counters.core.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.eclipse.tracecompass.analysis.counters.core.CounterStateProvider;
import org.eclipse.tracecompass.analysis.counters.core.CounterType;
import org.eclipse.tracecompass.analysis.counters.core.aspects.CounterAspect;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemFactory;
import org.eclipse.tracecompass.statesystem.core.backend.IStateHistoryBackend;
import org.eclipse.tracecompass.statesystem.core.backend.StateHistoryBackendFactory;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfContentFieldAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStubNs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Iterables;

/**
 * Test for the <code>CounterStateProvider</code> class.
 *
 * @author Mikael Ferland
 */
public class CounterStateProviderTest {

    private static final String COUNTER_FILE = "traces/counter_testTrace.xml";

    private TmfXmlTraceStubNs fTrace;
    private CounterStateProvider fStateProvider;

    /**
     * Setup the trace and the state provider for the tests.
     *
     * @throws TmfTraceException
     *             Exception thrown when initiating the trace
     */
    @Before
    public void setup() throws TmfTraceException {
        // Create the trace
        TmfXmlTraceStubNs trace = new TmfXmlTraceStubNs();
        trace.initTrace(null, COUNTER_FILE, ITmfEvent.class);

        // Add different varieties of aspects
        trace.addEventAspect(new TmfContentFieldAspect("aspect"));
        trace.addEventAspect(new CounterAspect("counter", "counter"));
        trace.addEventAspect(new CounterAspect("doubleCounter", "doubleCounter", CounterType.DOUBLE));
        trace.addEventAspect(new CounterAspect("counter", "counter", TmfCpuAspect.class));
        trace.addEventAspect(new CounterAspect("counter", "counter", TmfCpuAspect.class) {
            @Override
            public boolean isCumulative() {
                return true;
            }
        });
        trace.addEventAspect(new CounterAspect("doubleCounter", "doubleCounter", CounterType.DOUBLE, TmfCpuAspect.class));
        assertEquals(10, Iterables.size(trace.getEventAspects()));

        // Create the state provider
        fStateProvider = CounterStateProvider.create(trace);
        IStateHistoryBackend backend = StateHistoryBackendFactory.createInMemoryBackend("CounterStateSystem", 0);
        ITmfStateSystemBuilder ssb = StateSystemFactory.newStateSystem(backend);
        fStateProvider.assignTargetStateSystem(ssb);
        fTrace = trace;
    }

    /**
     * Dispose the trace and the state provider.
     */
    @After
    public void teardown() {
        fTrace.dispose();
        fStateProvider.dispose();
    }

    /**
     * Test the cloning of a <code>CounterStateProvider</code> object.
     */
    @Test
    public void testGetNewInstance() {
        ITmfStateProvider clone = fStateProvider.getNewInstance();
        assertNotSame("The original CounterStateProvider and its clone do not share the same reference.", fStateProvider, clone);
        assertEquals(fStateProvider.getVersion(), clone.getVersion());
        assertEquals(fStateProvider.getTrace(), clone.getTrace());
    }

    /**
     * Test the handling of events (i.e. ensure the state system is properly
     * built).
     */
    @Test
    public void testEventHandle() {
        // Process all the events from the trace
        ITmfContext ctx = fTrace.seekEvent(0);
        ITmfEvent event;
        while ((event = fTrace.getNext(ctx)) != null) {
            fStateProvider.processEvent(event);
        }
        fStateProvider.waitForEmptyQueue();

        /**
         * State system after processing (quark indicated in parentheses):
         *
         * <pre>
         * {root}
         *   +- Ungrouped                (0)
         *   |   +- counter              (1)
         *   |   +- doubleCounter        (2)
         *   +- Grouped                  (3)
         *       +- CPU                  (4)
         *           +- 0                (5)
         *           |  +- counter       (6)
         *           |  +- doubleCounter (7)
         *           +- 1                (8)
         *              +- counter       (9)
         *           |  +- doubleCounter (10)
         * </pre>
         */
        ITmfStateSystem ss = fStateProvider.getAssignedStateSystem();
        assertNotNull(ss);
        assertEquals(11, ss.getNbAttributes());
        assertEquals("Ungrouped", ss.getAttributeName(0));
        assertEquals("counter", ss.getAttributeName(1));
        assertEquals("doubleCounter", ss.getAttributeName(2));
        assertEquals("Grouped", ss.getAttributeName(3));
        assertEquals("CPU", ss.getAttributeName(4));
        assertEquals("0", ss.getAttributeName(5));
        assertEquals("counter", ss.getAttributeName(6));
        assertEquals("doubleCounter", ss.getAttributeName(7));
        assertEquals("1", ss.getAttributeName(8));
        assertEquals("counter", ss.getAttributeName(9));
        assertEquals("doubleCounter", ss.getAttributeName(10));
        assertEquals(ITmfStateValue.Type.DOUBLE, ss.queryOngoingState(7).getType());
        assertEquals(0.1, ss.queryOngoingState(7).unboxDouble(), 1E-15);
    }
}
