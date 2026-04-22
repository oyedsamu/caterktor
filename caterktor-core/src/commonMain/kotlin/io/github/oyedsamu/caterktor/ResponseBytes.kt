package io.github.oyedsamu.caterktor

/**
 * Legacy alias for raw response bytes.
 */
@Deprecated(
    "Use ResponseBody.Bytes for replayable bodies or ResponseBody.Source for streaming bodies.",
    ReplaceWith("ResponseBody.Bytes(this)"),
)
public typealias ResponseBytes = ByteArray
