package com.lulu786.alternate.callerid

/** One rule for every caller: receiver, dialer directory and VCF import. */
object PhoneNormalizer {
    /**
     * Returns digits as stored in `phone.fullPhoneNumber` / `phone.phoneNumber`:
     * a leading '+' or '00' is dropped, and a single trunk '0' is dropped except in Italy,
     * where leading zeros belong to the number.
     */
    fun normalize(raw: String, countryIso: String?): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.removePrefix("00")
            digits.startsWith("0") && !countryIso.equals("IT", ignoreCase = true) -> digits.removePrefix("0")
            else -> digits
        }
    }
}
