package com.example.iptv.data.model

/**
 * Result of every repository operation. A dedicated type (instead of [kotlin.Result])
 * keeps the error messages simple to display and works nicely with `when` expressions.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): String? = (this as? Failure)?.message
}

/** Simple loading state used by the screens. */
sealed class LoadState<out T> {
    object Idle : LoadState<Nothing>()
    object Loading : LoadState<Nothing>()
    data class Ready<T>(val items: List<T>) : LoadState<T>()
    data class Error(val message: String) : LoadState<Nothing>()
}
