package com.luciad.knowledge.domain;

/**
 * A documented method (or constructor) on an {@link ApiElement}.
 *
 * <p>Skeleton identity only. The canonical symbol distinguishes overloads (AD-3) and the
 * record is scoped to an SDK version (AD-12). Full field set is a deferred approval gate
 * (Epic 2).
 *
 * @param canonicalSymbol overload-distinguishing canonical symbol string (AD-3)
 * @param sdkVersion      SDK version this method belongs to (AD-12)
 */
public record MethodElement(String canonicalSymbol, String sdkVersion) {
}
