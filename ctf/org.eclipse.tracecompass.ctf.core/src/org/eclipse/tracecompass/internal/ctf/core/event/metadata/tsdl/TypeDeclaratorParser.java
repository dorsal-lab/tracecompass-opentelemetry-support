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
import static org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.TsdlUtils.concatenateUnaryStrings;
import static org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.TsdlUtils.isAnyUnaryString;
import static org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.TsdlUtils.isUnaryInteger;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.tracecompass.ctf.core.event.metadata.DeclarationScope;
import org.eclipse.tracecompass.ctf.core.event.types.IDeclaration;
import org.eclipse.tracecompass.ctf.core.event.types.IntegerDeclaration;
import org.eclipse.tracecompass.ctf.core.trace.CTFTrace;
import org.eclipse.tracecompass.ctf.parser.CTFParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.AbstractScopedCommonTreeParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.CTFAntlrMetadataNode;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.ParseException;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.event.EventScopeParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.stream.StreamScopeParser;
import org.eclipse.tracecompass.internal.ctf.core.event.metadata.tsdl.trace.TraceScopeParser;
import org.eclipse.tracecompass.internal.ctf.core.event.types.ArrayDeclaration;
import org.eclipse.tracecompass.internal.ctf.core.event.types.ICTFMetadataNode;
import org.eclipse.tracecompass.internal.ctf.core.event.types.SequenceDeclaration;

/**
 * A type declarator parser
 *
 * @author Matthew Khouzam
 *
 */
public final class TypeDeclaratorParser extends AbstractScopedCommonTreeParser {

    /**
     * The parameter object for type declarator parsers
     *
     * @author Matthew Khouzam
     *
     */
    @NonNullByDefault
    public static final class Param implements ICommonTreeParserParameter {
        private final DeclarationScope fDeclarationScope;
        private final ICTFMetadataNode fListNode;
        private final StringBuilder fBuilder;
        private final CTFTrace fTrace;

        /**
         * Parameter constructor
         *
         * @param trace
         *            the trace
         * @param listNode
         *            the listNode
         * @param scope
         *            the scope
         * @param builder
         *            the string builder to populate
         */
        public Param(CTFTrace trace, ICTFMetadataNode listNode, DeclarationScope scope, StringBuilder builder) {
            fTrace = trace;
            fListNode = listNode;
            fDeclarationScope = scope;
            fBuilder = builder;
        }
    }

    /**
     * The instance
     */
    public static final TypeDeclaratorParser INSTANCE = new TypeDeclaratorParser();

    private TypeDeclaratorParser() {
    }

    /**
     * Parses a pair type declarator / type specifier list and returns the
     * corresponding declaration. If it is present, it also writes the
     * identifier of the declarator in the given {@link StringBuilder}.
     *
     * @param typeDeclarator
     *            A TYPE_DECLARATOR node.
     * @param param
     *            the parameter object
     * @return The corresponding declaration.
     * @throws ParseException
     *             If there is an error finding or creating the declaration.
     */
    @Override
    public IDeclaration parse(ICTFMetadataNode typeDeclarator, ICommonTreeParserParameter param) throws ParseException {
        if (!(param instanceof Param)) {
            throw new IllegalArgumentException("Param must be a " + Param.class.getCanonicalName()); //$NON-NLS-1$
        }
        DeclarationScope scope = ((Param) param).fDeclarationScope;
        CTFTrace trace = ((Param) param).fTrace;
        ICTFMetadataNode typeSpecifierList = ((Param) param).fListNode;

        IDeclaration declaration = null;
        List<ICTFMetadataNode> children = null;
        List<@NonNull ICTFMetadataNode> pointers = new LinkedList<>();
        List<ICTFMetadataNode> lengths = new LinkedList<>();
        ICTFMetadataNode identifier = null;

        if (typeDeclarator instanceof CTFAntlrMetadataNode) {
            /* Separate the tokens by type */
            children = typeDeclarator.getChildren();
            for (ICTFMetadataNode child : children) {

                String type = child.getType();
                if (CTFParser.tokenNames[CTFParser.POINTER].equals(type)) {
                    pointers.add(child);
                } else if (CTFParser.tokenNames[CTFParser.IDENTIFIER].equals(type)) {
                    identifier = child;
                } else if (CTFParser.tokenNames[CTFParser.LENGTH].equals(type)) {
                    lengths.add(child);
                } else {
                    throw childTypeError(child);
                }
            }
        } else {
            identifier = typeDeclarator;
        }

        /*
         * Parse the type specifier list, which is the "base" type. For example,
         * it would be int in int a[3][len].
         */
        declaration = TypeSpecifierListParser.INSTANCE.parse(typeSpecifierList, new TypeSpecifierListParser.Param(trace, pointers, identifier, scope));

        /*
         * Each length subscript means that we must create a nested array or
         * sequence. For example, int a[3][len] means that we have an array of 3
         * (sequences of length 'len' of (int)).
         */
        if (!lengths.isEmpty()) {
            /* We begin at the end */
            Collections.reverse(lengths);

            for (ICTFMetadataNode length : lengths) {
                /*
                 * By looking at the first expression, we can determine whether
                 * it is an array or a sequence.
                 */
                List<ICTFMetadataNode> lengthChildren = length.getChildren();

                ICTFMetadataNode first = lengthChildren.get(0);
                if (isUnaryInteger(first)) {
                    /* Array */
                    int arrayLength = UnaryIntegerParser.INSTANCE.parse(first, null).intValue();

                    if (arrayLength < 1) {
                        throw new ParseException("Array length is negative"); //$NON-NLS-1$
                    }

                    /* Create the array declaration. */
                    declaration = new ArrayDeclaration(arrayLength, declaration);
                } else if (isAnyUnaryString(first)) {
                    /* Sequence */
                    String lengthName = concatenateUnaryStrings(lengthChildren);

                    /* check that lengthName was declared */
                    if (isSignedIntegerField(lengthName, scope)) {
                        throw new ParseException("Sequence declared with length that is not an unsigned integer"); //$NON-NLS-1$
                    }
                    /* Create the sequence declaration. */
                    declaration = new SequenceDeclaration(lengthName,
                            declaration);
                } else if (isTrace(first)) {
                    /* Sequence */
                    String lengthName = TraceScopeParser.INSTANCE.parse(null, new TraceScopeParser.Param(lengthChildren));

                    /* check that lengthName was declared */
                    if (isSignedIntegerField(lengthName, scope)) {
                        throw new ParseException("Sequence declared with length that is not an unsigned integer"); //$NON-NLS-1$
                    }
                    /* Create the sequence declaration. */
                    declaration = new SequenceDeclaration(lengthName,
                            declaration);

                } else if (isStream(first)) {
                    /* Sequence */
                    String lengthName = StreamScopeParser.INSTANCE.parse(null, new StreamScopeParser.Param(lengthChildren));

                    /* check that lengthName was declared */
                    if (isSignedIntegerField(lengthName, scope)) {
                        throw new ParseException("Sequence declared with length that is not an unsigned integer"); //$NON-NLS-1$
                    }
                    /* Create the sequence declaration. */
                    declaration = new SequenceDeclaration(lengthName,
                            declaration);
                } else if (isEvent(first)) {
                    /* Sequence */
                    String lengthName = EventScopeParser.INSTANCE.parse(null, new EventScopeParser.Param(lengthChildren));

                    /* check that lengthName was declared */
                    if (isSignedIntegerField(lengthName, scope)) {
                        throw new ParseException("Sequence declared with length that is not an unsigned integer"); //$NON-NLS-1$
                    }
                    /* Create the sequence declaration. */
                    declaration = new SequenceDeclaration(lengthName,
                            declaration);
                } else {
                    throw childTypeError(first);
                }
            }
        }

        if (identifier != null) {
            final String text = identifier.getText();
            if (text == null) {
                throw new ParseException("Cannot have unidentified declarator"); //$NON-NLS-1$
            }
            ((Param) param).fBuilder.append(text);
            registerType(declaration, text, scope);
        }

        return declaration;
    }

    private static boolean isSignedIntegerField(String lengthName, DeclarationScope scope) throws ParseException {
        IDeclaration decl = scope.lookupIdentifierRecursive(lengthName);
        if (decl instanceof IntegerDeclaration) {
            return ((IntegerDeclaration) decl).isSigned();
        }
        throw new ParseException("Is not an integer: " + lengthName); //$NON-NLS-1$

    }

    private static boolean isEvent(ICTFMetadataNode first) {
        return CTFParser.tokenNames[CTFParser.EVENT].equals(first.getType());
    }

    private static boolean isStream(ICTFMetadataNode first) {
        return CTFParser.tokenNames[CTFParser.STREAM].equals(first.getType());
    }

    private static boolean isTrace(ICTFMetadataNode first) {
        return CTFParser.tokenNames[CTFParser.TRACE].equals(first.getType());
    }

}
