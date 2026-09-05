package com.xieguiawu.picturetrans.server

import java.security.MessageDigest

/** 常数时间 token 比较，防时序侧信道。 */
class TokenGuard(private val token: String) {

    fun isValid(candidate: String?): Boolean =
        candidate != null &&
            MessageDigest.isEqual(
                token.toByteArray(Charsets.UTF_8),
                candidate.toByteArray(Charsets.UTF_8),
            )
}
