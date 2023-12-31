/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.model;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.model.xy.ISeriesModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;

/**
 * This is a basic implementation of {@link ITmfXyModel}
 *
 * @author Geneviève Bastien
 * @since 4.0
 */
public class TmfXyModel implements ITmfXyModel {

    @SerializedName("title")
    private final String fTitle;

    @SerializedName("series")
    private final Collection<ISeriesModel> fSeries;

    /**
     * Constructor
     *
     * @param title
     *            Chart title
     * @param series
     *            A map of series
     * @since 6.0
     */
    public TmfXyModel(String title, Collection<ISeriesModel> series) {
        fTitle = title;
        fSeries = ImmutableList.copyOf(series);
    }

    @Override
    public @Nullable String getTitle() {
        return fTitle;
    }

    @Override
    public @NonNull Collection<ISeriesModel> getSeriesData() {
        return fSeries;
    }
}
