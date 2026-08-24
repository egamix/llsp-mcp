package com.luciadmcp.knowledge.domain;

/**
 * The fixed set of relationship-edge types in the knowledge graph.
 *
 * <p>Exactly these 15 {@code UPPER_SNAKE_CASE} values are permitted (brief §6,
 * ARCHITECTURE-SPINE.md#Consistency-Conventions). Adding, removing, or renaming a value
 * requires an architecture-decision amendment — no ad-hoc extras.
 */
public enum RelationshipType {
    EXTENDS_CLASS,
    EXTENDS_INTERFACE,
    IMPLEMENTS,
    IMPLEMENTED_BY,
    DIRECT_SUBCLASS,
    RETURN_TYPE,
    PARAMETER_TYPE,
    THROWS_TYPE,
    FIELD_TYPE,
    CONSTRUCTOR_PARAMETER_TYPE,
    OVERRIDES,
    SPECIFIED_BY,
    SEE_ALSO,
    EXAMPLE_USES,
    WORKFLOW_REQUIRES
}
