/*******************************************************************************
 * Copyright (c) 2013, 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Geneviève Bastien - Initial implementation and API
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.event.matching;

import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;


/**
 * @author eysan
 *
 */
public interface ITmfTreeMatchEventDefinition extends ITmfMatchEventDefinition {

    /**
     * @param event
     *            The parent
     * @return The parent span event key
     */
    public IEventMatchingKey getParentEventKey(ITmfEvent event);

}
