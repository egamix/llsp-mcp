package com.luciad.knowledge.domain;

/**
 * A documented API type (class, interface, enum, annotation) in the SDK.
 *
 * <p>Skeleton identity only: the canonical symbol is the primary join key (AD-3) and the
 * record is scoped to an SDK version (AD-12). Full field set is a deferred approval gate
 * (Epic 2) and is not invented here.
 *
 * @param canonicalSymbol canonical symbol string identifying this element (AD-3)
 * @param sdkVersion      SDK version this element belongs to (AD-12)
 */
public record ApiElement(String canonicalSymbol, String sdkVersion) {
}
