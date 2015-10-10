/*
 * Copyright (c) 2015 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.util.type;

import com.google.common.base.Preconditions;
import javax.annotation.Nonnull;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;

public abstract class DerivedTypeBuilder<T extends TypeDefinition<T>> extends TypeBuilder<T> {
    private Object defaultValue;
    private String description;
    private String reference;
    private Status status = Status.CURRENT;
    private String units;

    DerivedTypeBuilder(final T baseType, final SchemaPath path) {
        super(Preconditions.checkNotNull(baseType), path);
    }

    public void setDefaultValue(@Nonnull final Object defaultValue) {
        this.defaultValue = Preconditions.checkNotNull(defaultValue);
    }

    public final void setDescription(@Nonnull final String description) {
        this.description = Preconditions.checkNotNull(description);
    }

    public final void setReference(@Nonnull final String reference) {
        this.reference = Preconditions.checkNotNull(reference);
    }

    public final void setStatus(@Nonnull final Status status) {
        this.status = Preconditions.checkNotNull(status);
    }

    public final void setUnits(final String units) {
        this.units = Preconditions.checkNotNull(units);
    }

    final Object getDefaultValue() {
        return defaultValue;
    }

    final String getDescription() {
        return description;
    }

    final String getReference() {
        return reference;
    }

    final Status getStatus() {
        return status;
    }

    final String getUnits() {
        return units;
    }
}