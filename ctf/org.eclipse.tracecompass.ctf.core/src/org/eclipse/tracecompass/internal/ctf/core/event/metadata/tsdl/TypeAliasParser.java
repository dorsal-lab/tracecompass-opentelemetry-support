/*******************************************************************************
 * Copyright (c) 2015, 2023 Ericsson
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl;

import static org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.TsdlUtils.childTypeError;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.tracecompass.ctf.core.event.metadata.DeclarationScope;
import org.eclipse.tracecompass.ctf.core.event.types.IDeclaration;
import org.eclipse.tracecompass.ctf.core.event.types.VariantDeclaration;
import org.eclipse.tracecompass.ctf.core.trace.CTFTrace;
import org.eclipse.tracecompass.ctf.parser.CTFParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.AbstractScopedCommonTreeParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.JsonStructureFieldMemberMetadataNode;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.ParseException;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.integer.IntegerDeclarationParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.string.StringDeclarationParser;
import org.eclipse.tracecompass.internal.ctf.core.event.types.ICTFMetadataNode;
import org.eclipse.tracecompass.internal.ctf.core.utils.JsonMetadataStrings;

import com.google.gson.JsonObject;

/**
 * The "typealias" declaration can be used to give a name (including pointer
 * declarator specifier) to a type. It should also be used to map basic C types
 * (float, int, unsigned long, ...) to a CTF type. Typealias is a superset of
 * "typedef": it also allows assignment of a simple variable identifier to a
 * type.
 *
 * @author Matthew Khouzam - Inital API and implementation
 * @author Efficios - Documentation
 *
 */
public final class TypeAliasParser extends AbstractScopedCommonTreeParser {

    /**
     * Parameters for the typealias parser
     *
     * @author Matthew Khouzam
     *
     */
    @NonNullByDefault
    public static final class Param implements ICommonTreeParserParameter {
        private final DeclarationScope fDeclarationScope;
        private final CTFTrace fTrace;

        /**
         * Parameter constructor
         *
         * @param trace
         *            the trace
         * @param scope
         *            the scope
         */
        public Param(CTFTrace trace, DeclarationScope scope) {
            fTrace = trace;
            fDeclarationScope = scope;
        }
    }

    /**
     * Instance
     */
    public static final TypeAliasParser INSTANCE = new TypeAliasParser();

    private TypeAliasParser() {
    }

    @Override
    public IDeclaration parse(ICTFMetadataNode typealias, ICommonTreeParserParameter param) throws ParseException {
        if (!(param instanceof Param)) {
            throw new IllegalArgumentException("Param must be a " + Param.class.getCanonicalName()); //$NON-NLS-1$
        }
        DeclarationScope scope = ((Param) param).fDeclarationScope;

        List<ICTFMetadataNode> children = typealias.getChildren();

        ICTFMetadataNode target = null;
        ICTFMetadataNode alias = null;
        IDeclaration targetDeclaration = null;
        CTFTrace trace = ((Param) param).fTrace;

        String aliasString;
        if (typealias instanceof JsonStructureFieldMemberMetadataNode) {
            JsonStructureFieldMemberMetadataNode member = ((JsonStructureFieldMemberMetadataNode) typealias);
            aliasString = member.getName();
            String type = typealias.getType();
            if (member.getFieldClass().isJsonObject()) {
                JsonObject fieldClass = member.getFieldClass().getAsJsonObject();
                if (JsonMetadataStrings.FIXED_UNSIGNED_INTEGER_FIELD.equals(type)) {
                    fieldClass.addProperty("signed", false); //$NON-NLS-1$
                    targetDeclaration = IntegerDeclarationParser.INSTANCE.parse(typealias, new IntegerDeclarationParser.Param(trace));
                } else if (JsonMetadataStrings.STATIC_LENGTH_BLOB.equals(type)) {
                    targetDeclaration = BlobDeclarationParser.INSTANCE.parse(typealias, null);
                } else if (JsonMetadataStrings.NULL_TERMINATED_STRING.equals(type)) {
                    targetDeclaration = StringDeclarationParser.INSTANCE.parse(typealias, null);
                } else {
                    throw new ParseException("Invalid field class"); //$NON-NLS-1$
                }
            } else {
                // Should be changed once field-class-alias
                // fragments are implemented
                throw new ParseException("Field classes that are not Json Objects are not yet supported"); //$NON-NLS-1$
            }
        } else {
            for (ICTFMetadataNode child : children) {
                String type = child.getType();
                if (CTFParser.tokenNames[CTFParser.TYPEALIAS_TARGET].equals(type)) {
                    target = child;
                } else if (CTFParser.tokenNames[CTFParser.TYPEALIAS_ALIAS].equals(type)) {
                    alias = child;
                } else {
                    throw childTypeError(child);
                }
            }

            targetDeclaration = TypeAliasTargetParser.INSTANCE.parse(target, new TypeAliasTargetParser.Param(trace, scope));

            if ((targetDeclaration instanceof VariantDeclaration)
                    && ((VariantDeclaration) targetDeclaration).isTagged()) {
                throw new ParseException("Typealias of untagged variant is not permitted"); //$NON-NLS-1$
            }

            aliasString = TypeAliasAliasParser.INSTANCE.parse(alias, null);
        }

        scope.registerType(aliasString, targetDeclaration);
        return targetDeclaration;
    }

}
