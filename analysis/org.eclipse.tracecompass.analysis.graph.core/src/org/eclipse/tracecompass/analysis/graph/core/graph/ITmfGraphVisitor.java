/*******************************************************************************
 * Copyright (c) 2022 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.graph.core.graph;

/**
 * Interface for all graph visitors. Using on the graph exploration method, the
 * visit methods will be called for each vertex and edge visited
 *
 * @since 4.0
 */
public interface ITmfGraphVisitor {

    /**
     * Visits a vertex that is the head of a worker streak. The head here is not
     * the first node of an object. It is just a node with no edge going left. A
     * worker may have many head vertices
     *
     * @param vertex
     *            The visited vertex
     */
    void visitHead(ITmfVertex vertex);

    /**
     * Visits a vertex
     *
     * @param vertex
     *            The visited vertex
     */
    void visit(ITmfVertex vertex);

    /**
     * Visits an edge
     *
     * @param edge
     *            The visited edge
     * @param horizontal
     *            Whether the edge is horizontal (beginning and end are of the
     *            same worker) or vertical
     */
    void visit(ITmfEdge edge, boolean horizontal);

}
