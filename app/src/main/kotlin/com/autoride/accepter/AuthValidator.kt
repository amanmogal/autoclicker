package com.autoride.accepter

import java.util.regex.Pattern

object AuthValidator {
    private val emailPattern: Pattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun isValidEmail(email: String): Boolean {
        return emailPattern.matcher(email.trim()).matches()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8
    }
}
