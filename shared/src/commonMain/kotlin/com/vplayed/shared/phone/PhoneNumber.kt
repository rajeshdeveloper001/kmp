package com.vplayed.shared.phone

/** A country the phone sign-in offers: ISO region code, calling code (digits only) and English name. */
data class CountryDialCode(val regionCode: String, val dialCode: String, val name: String) {
    /** `+91`, as the country chip shows it. */
    val prefix: String get() = "+$dialCode"
}

/** The countries of the sign-in prototype's country picker (`vp-login-method.js`), India first. */
object CountryDialCodes {
    val INDIA = CountryDialCode("IN", "91", "India")

    val all: List<CountryDialCode> = listOf(
        INDIA,
        CountryDialCode("US", "1", "United States"),
        CountryDialCode("GB", "44", "United Kingdom"),
        CountryDialCode("AE", "971", "UAE"),
        CountryDialCode("SG", "65", "Singapore"),
        CountryDialCode("AU", "61", "Australia"),
    )

    /** India, as in the prototype and the UIKit app. */
    val DEFAULT: CountryDialCode = INDIA

    /** The entry for an ISO region code (case-insensitive), or null when the picker does not offer it. */
    fun forRegion(regionCode: String?): CountryDialCode? =
        regionCode?.trim()?.uppercase()?.let { code -> all.firstOrNull { it.regionCode == code } }
}

/** A phone number split as the backend wants it: `phone-login` gets [nationalNumber], the SMS goes to [e164]. */
data class PhoneNumber(val dialCode: String, val nationalNumber: String) {
    /** `+919876543210`, for the SMS provider. */
    val e164: String get() = "+$dialCode$nationalNumber"

    /** `+91 98765 43210`, as the code step shows it. */
    val display: String get() = "+$dialCode ${PhoneFormatting.group(nationalNumber)}"

    // Personal data: never logged.
    override fun toString(): String = "PhoneNumber(+$dialCode, ***)"
}

/** Display grouping of national numbers. */
object PhoneFormatting {
    private const val GROUP = 5

    /** `9876543210` → `98765 43210` (prototype: a space after the fifth digit). Non-digits are dropped. */
    fun group(digits: String): String {
        val clean = digits.filter { it in '0'..'9' }
        return if (clean.length > GROUP) clean.take(GROUP) + " " + clean.drop(GROUP) else clean
    }
}
