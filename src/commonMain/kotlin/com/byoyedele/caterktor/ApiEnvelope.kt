package com.byoyedele.caterktor

import kotlinx.serialization.Serializable

@Serializable
data class ApiRequest<T>(
    val data: T,
)

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
)

@Serializable
data class ApiErrorResponse(
    val errors: List<ApiError>,
)

@Serializable
data class ApiMonolithErrorResponse(
    val message: String,
)

@Serializable
data class ApiError(
    val code: String? = null,
    val detail: String? = null,
    val status: Int? = null,
    val title: String? = null,
)
