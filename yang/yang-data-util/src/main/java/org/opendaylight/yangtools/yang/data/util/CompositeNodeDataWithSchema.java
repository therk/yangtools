/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.util;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map.Entry;
import org.opendaylight.yangtools.odlext.model.api.YangModeledAnyxmlSchemaNode;
import org.opendaylight.yangtools.rfc7952.data.api.StreamWriterMetadataExtension;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Utility class used for tracking parser state as needed by a StAX-like parser.
 * This class is to be used only by respective XML and JSON parsers in yang-data-codec-xml and yang-data-codec-gson.
 *
 * <p>
 * Represents a node which is composed of multiple simpler nodes.
 */
public class CompositeNodeDataWithSchema<T extends DataSchemaNode> extends AbstractNodeDataWithSchema<T> {
    /**
     * nodes which were added to schema via augmentation and are present in data input.
     */
    private final Multimap<AugmentationSchemaNode, AbstractNodeDataWithSchema<?>> augmentationsToChild =
        ArrayListMultimap.create();

    /**
     * remaining data nodes (which aren't added via augment). Every of one them should have the same QName.
     */
    private final List<AbstractNodeDataWithSchema<?>> children = new ArrayList<>();

    public CompositeNodeDataWithSchema(final T schema) {
        super(schema);
    }

    private AbstractNodeDataWithSchema<?> addChild(final DataSchemaNode schema) {
        AbstractNodeDataWithSchema<?> newChild = addSimpleChild(schema);
        return newChild == null ? addCompositeChild(schema) : newChild;
    }

    public void addChild(final AbstractNodeDataWithSchema<?> newChild) {
        children.add(newChild);
    }

    public AbstractNodeDataWithSchema<?> addChild(final Deque<DataSchemaNode> schemas) {
        checkArgument(!schemas.isEmpty(), "Expecting at least one schema");

        // Pop the first node...
        final DataSchemaNode schema = schemas.pop();
        if (schemas.isEmpty()) {
            // Simple, direct node
            return addChild(schema);
        }

        // The choice/case mess, reuse what we already popped
        final DataSchemaNode choiceCandidate = schema;
        checkArgument(choiceCandidate instanceof ChoiceSchemaNode, "Expected node of type ChoiceNode but was %s",
            choiceCandidate.getClass());
        final ChoiceSchemaNode choiceNode = (ChoiceSchemaNode) choiceCandidate;

        final DataSchemaNode caseCandidate = schemas.pop();
        checkArgument(caseCandidate instanceof CaseSchemaNode, "Expected node of type ChoiceCaseNode but was %s",
            caseCandidate.getClass());
        final CaseSchemaNode caseNode = (CaseSchemaNode) caseCandidate;

        AugmentationSchemaNode augSchema = null;
        if (choiceCandidate.isAugmenting()) {
            augSchema = findCorrespondingAugment(getSchema(), choiceCandidate);
        }

        // looking for existing choice
        final Collection<AbstractNodeDataWithSchema<?>> childNodes;
        if (augSchema != null) {
            childNodes = augmentationsToChild.get(augSchema);
        } else {
            childNodes = children;
        }

        CompositeNodeDataWithSchema<?> caseNodeDataWithSchema = findChoice(childNodes, choiceCandidate, caseCandidate);
        if (caseNodeDataWithSchema == null) {
            ChoiceNodeDataWithSchema choiceNodeDataWithSchema = new ChoiceNodeDataWithSchema(choiceNode);
            childNodes.add(choiceNodeDataWithSchema);
            caseNodeDataWithSchema = choiceNodeDataWithSchema.addCompositeChild(caseNode);
        }

        return caseNodeDataWithSchema.addChild(schemas);
    }

    private AbstractNodeDataWithSchema<?> addSimpleChild(final DataSchemaNode schema) {
        SimpleNodeDataWithSchema<?> newChild = null;
        if (schema instanceof LeafSchemaNode) {
            newChild = new LeafNodeDataWithSchema((LeafSchemaNode) schema);
        } else if (schema instanceof AnyxmlSchemaNode) {
            // YangModeledAnyxmlSchemaNode is handled by addCompositeChild method.
            if (schema instanceof YangModeledAnyxmlSchemaNode) {
                return null;
            }
            newChild = new AnyXmlNodeDataWithSchema((AnyxmlSchemaNode) schema);
        } else if (schema instanceof AnydataSchemaNode) {
            newChild = new AnydataNodeDataWithSchema((AnydataSchemaNode) schema);
        } else {
            return null;
        }

        AugmentationSchemaNode augSchema = null;
        if (schema.isAugmenting()) {
            augSchema = findCorrespondingAugment(getSchema(), schema);
        }
        if (augSchema != null) {
            augmentationsToChild.put(augSchema, newChild);
        } else {
            addChild(newChild);
        }
        return newChild;
    }

    private static CaseNodeDataWithSchema findChoice(final Collection<AbstractNodeDataWithSchema<?>> childNodes,
            final DataSchemaNode choiceCandidate, final DataSchemaNode caseCandidate) {
        if (childNodes != null) {
            for (AbstractNodeDataWithSchema<?> nodeDataWithSchema : childNodes) {
                if (nodeDataWithSchema instanceof ChoiceNodeDataWithSchema
                        && nodeDataWithSchema.getSchema().getQName().equals(choiceCandidate.getQName())) {
                    CaseNodeDataWithSchema casePrevious = ((ChoiceNodeDataWithSchema) nodeDataWithSchema).getCase();

                    checkArgument(casePrevious.getSchema().getQName().equals(caseCandidate.getQName()),
                        "Data from case %s are specified but other data from case %s were specified earlier."
                        + " Data aren't from the same case.", caseCandidate.getQName(),
                        casePrevious.getSchema().getQName());

                    return casePrevious;
                }
            }
        }
        return null;
    }

    AbstractNodeDataWithSchema<?> addCompositeChild(final DataSchemaNode schema) {
        final CompositeNodeDataWithSchema<?> newChild;

        if (schema instanceof ListSchemaNode) {
            newChild = new ListNodeDataWithSchema((ListSchemaNode) schema);
        } else if (schema instanceof LeafListSchemaNode) {
            newChild = new LeafListNodeDataWithSchema((LeafListSchemaNode) schema);
        } else if (schema instanceof ContainerSchemaNode) {
            newChild = new ContainerNodeDataWithSchema((ContainerSchemaNode) schema);
        } else if (schema instanceof YangModeledAnyxmlSchemaNode) {
            newChild = new YangModeledAnyXmlNodeDataWithSchema((YangModeledAnyxmlSchemaNode)schema);
        } else {
            newChild = new CompositeNodeDataWithSchema<>(schema);
        }

        addCompositeChild(newChild);
        return newChild;
    }

    void addCompositeChild(final CompositeNodeDataWithSchema<?> newChild) {
        AugmentationSchemaNode augSchema = findCorrespondingAugment(getSchema(), newChild.getSchema());
        if (augSchema != null) {
            augmentationsToChild.put(augSchema, newChild);
        } else {
            addChild(newChild);
        }
    }

    /**
     * Return a hint about how may children we are going to generate.
     * @return Size of currently-present node list.
     */
    protected final int childSizeHint() {
        return children.size();
    }

    @Override
    public void write(final NormalizedNodeStreamWriter writer, final StreamWriterMetadataExtension metaWriter)
            throws IOException {
        for (AbstractNodeDataWithSchema<?> child : children) {
            child.write(writer, metaWriter);
        }
        for (Entry<AugmentationSchemaNode, Collection<AbstractNodeDataWithSchema<?>>> augmentationToChild
                : augmentationsToChild.asMap().entrySet()) {
            final Collection<AbstractNodeDataWithSchema<?>> childsFromAgumentation = augmentationToChild.getValue();
            if (!childsFromAgumentation.isEmpty()) {
                // FIXME: can we get the augmentation schema?
                writer.startAugmentationNode(DataSchemaContextNode.augmentationIdentifierFrom(
                    augmentationToChild.getKey()));

                for (AbstractNodeDataWithSchema<?> nodeDataWithSchema : childsFromAgumentation) {
                    nodeDataWithSchema.write(writer, metaWriter);
                }

                writer.endNode();
            }
        }
    }

    /**
     * Tries to find in {@code parent} which is dealed as augmentation target node with QName as {@code child}. If such
     * node is found then it is returned, else null.
     *
     * @param parent parent node
     * @param child child node
     * @return augmentation schema
     */
    private static AugmentationSchemaNode findCorrespondingAugment(final DataSchemaNode parent,
            final DataSchemaNode child) {
        if (parent instanceof AugmentationTarget && !(parent instanceof ChoiceSchemaNode)) {
            for (AugmentationSchemaNode augmentation : ((AugmentationTarget) parent).getAvailableAugmentations()) {
                DataSchemaNode childInAugmentation = augmentation.getDataChildByName(child.getQName());
                if (childInAugmentation != null) {
                    return augmentation;
                }
            }
        }
        return null;
    }
}
