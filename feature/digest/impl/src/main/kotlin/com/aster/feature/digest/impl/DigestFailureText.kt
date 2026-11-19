package com.aster.feature.digest.impl

import com.aster.core.model.DigestFailure

internal fun DigestFailure.message() = when (this) {
    DigestFailure.NOT_CONFIGURED, DigestFailure.NO_SOURCES -> R.string.digest_setup_hint
    DigestFailure.NO_NEWS -> R.string.digest_no_news
    DigestFailure.INPUT_TOO_LARGE -> R.string.digest_too_large
    DigestFailure.BUSY -> R.string.digest_busy
    DigestFailure.CONNECTION -> R.string.digest_connection_error
    DigestFailure.TIMEOUT -> R.string.digest_timeout
    DigestFailure.AUTHENTICATION -> R.string.digest_auth_error
    DigestFailure.BALANCE -> R.string.digest_balance_error
    DigestFailure.RATE_LIMIT -> R.string.digest_rate_limit
    DigestFailure.SERVER -> R.string.digest_server_error
    DigestFailure.INVALID_RESPONSE -> R.string.digest_invalid_response
    DigestFailure.STORAGE -> R.string.digest_storage_error
    DigestFailure.STEP_LIMIT -> R.string.agent_step_limit
}
