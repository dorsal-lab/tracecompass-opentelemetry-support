/*******************************************************************************
 * Copyright (c) 2015, 2022 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.core.execution.graph;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTmfGraphProvider;
import org.eclipse.tracecompass.analysis.graph.core.building.ITraceEventHandler;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.DefaultEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.Activator;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * The graph provider builds an execution graph from a kernel trace. The
 * execution graph is a 2d-mesh model of the system, where vertices represent
 * events, horizontal edges represent states of tasks, and where vertical edges
 * represent relations between tasks (currently local wake-up and pairs of
 * network packets).
 *
 * Event handling is split into smaller sub-handlers. One event request is done,
 * and each event is passed to the handlers in the order in the list, such as
 * this pseudo code:
 *
 * <pre>
 * for each event:
 *   for each handler:
 *     handler.handleEvent(event)
 * </pre>
 *
 * @author Geneviève Bastien
 * @author Francis Giraldeau
 * @since 2.4
 */
public class OsExecutionGraphProvider extends AbstractTmfGraphProvider {

    /** Extension point ID */
    private static final String TMF_GRAPH_HANDLER_ID = "org.eclipse.tracecompass.analysis.os.linux.core.graph.handler"; //$NON-NLS-1$
    private static final String HANDLER = "handler"; //$NON-NLS-1$
    private static final String ATTRIBUTE_CLASS = "class"; //$NON-NLS-1$
    private static final String ATTRIBUTE_PRIORITY = "priority"; //$NON-NLS-1$
    private static final int DEFAULT_PRIORITY = 10;
    // Increment when the graph structure changes (new edges, etc)
    private static final int GRAPH_VERSION = 1;

    private final OsSystemModel fSystem;

    /**
     * Represents an interrupt context
     */
    public enum Context {
        /** Not in an interrupt */
        NONE,
        /** The interrupt is a soft IRQ */
        SOFTIRQ,
        /** The interrupt is an IRQ */
        IRQ,
        /** The interrupt is a timer */
        HRTIMER,
        /** The inter-processor interrupt */
        IPI,
        /** The complete IRQ context, soft and hard IRQ are usually within this context
         * @since 3.1*/
        COMPLETE_IRQ,
        /**
         * The context of packet reception
         * @since 3.1
         */
        PACKET_RECEPTION
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace on which to build graph
     */
    public OsExecutionGraphProvider(ITmfTrace trace) {
        super(trace, "LTTng Kernel"); //$NON-NLS-1$
        fSystem = new OsSystemModel();

        IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(TMF_GRAPH_HANDLER_ID);
        for (IConfigurationElement ce : config) {
            String elementName = ce.getName();
            if (HANDLER.equals(elementName)) {
                IOsExecutionGraphHandlerBuilder builder;
                try {
                    builder = (IOsExecutionGraphHandlerBuilder) ce.createExecutableExtension(ATTRIBUTE_CLASS);
                } catch (CoreException e1) {
                    Activator.getDefault().logWarning("Error create execution graph handler builder", e1); //$NON-NLS-1$
                    continue;
                }
                String priorityStr = ce.getAttribute(ATTRIBUTE_PRIORITY);
                int priority = DEFAULT_PRIORITY;
                try {
                    priority = Integer.valueOf(priorityStr);
                } catch (NumberFormatException e) {
                    // Nothing to do, use default value
                }
                ITraceEventHandler handler = builder.createHandler(this, priority);
                registerHandler(handler);
            }
        }
    }

    @Override
    public void done() {
        // Nothing to do
    }

    /**
     * Returns the event layout for the given trace
     *
     * @param trace
     *            the trace
     *
     * @return the eventLayout
     */
    public IKernelAnalysisEventLayout getEventLayout(ITmfTrace trace) {
        if (trace instanceof IKernelTrace) {
            return ((IKernelTrace) trace).getKernelEventLayout();
        }
        return DefaultEventLayout.getInstance();
    }

    /**
     * Returns the system model
     *
     * @return the system
     */
    public OsSystemModel getSystem() {
        return fSystem;
    }

    @Override
    public int getGraphFileVersion() {
        return GRAPH_VERSION;
    }

}
