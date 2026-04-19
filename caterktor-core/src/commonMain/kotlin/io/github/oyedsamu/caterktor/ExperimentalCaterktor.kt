package io.github.oyedsamu.caterktor

/**
 * Marks a CaterKtor API as experimental. Experimental APIs may change or be
 * removed in any release without a deprecation cycle.
 *
 * Opt in per call site with `@OptIn(ExperimentalCaterktor::class)`, or
 * per module by propagating the annotation.
 *
 * APIs marked experimental will be promoted to stable in a future minor
 * release once field feedback confirms the design. See PRD-v2 §9.
 */
@RequiresOptIn(
    message = "This CaterKtor API is experimental and may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.ANNOTATION_CLASS,
)
public annotation class ExperimentalCaterktor
