package org.elkuku.kgpsdiary.data

import org.elkuku.kgpsdiary.UiText

/** The outcome of an API request. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** The ID token was missing or rejected (expired or invalid); [message] is the server's. */
    data class Unauthorized(val message: UiText?) : ApiResult<Nothing>

    /** E.g. a network error or an error answer of the server. */
    data class Failure(val message: UiText) : ApiResult<Nothing>
}
