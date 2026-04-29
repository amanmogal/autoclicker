package com.autoride.accepter

import java.util.Locale

object RideActionTextPolicy {
    private val acceptKeywords = setOf(
        // Keep this strict to avoid false positives like "Go To".
        "accept",
        "confirm",
        "match",
        "agree",
        "swipe to accept",
        "accept ride",
        "book now",
        // Indic language variants seen in the legacy APK.
        "स्वीकार", "स्वीकार करें", "पुष्टि", "पुष्टि करें", "मिलान", "मेल खाना",
        "स्वीकार करा", "स्वीकारा", "पुष्टी करा", "पुष्टी", "जुळवा", "जुळणे", "जुळणी",
        "ஏற்க", "ஒப்புக் கொள்", "ஏற்றுக்கொள்", "உறுதிப்படுத்து", "பொருந்து", "பொருத்தம்",
        "స్వీకరించు", "అంగీకరించు", "నిర్ధారించు", "ధృవీకరించు", "సరిపోల్చు", "సరిపోవు", "సరిపోలిక",
        "ಸ್ವೀಕರಿಸಿ", "ಒಪ್ಪಿಕೊ", "ದೃಢಪಡಿಸಿ", "ಖಚಿತಪಡಿಸು", "ಹೊಂದಿಕೆ", "ಹೊಂದಿಸು", "ಹೊಂದಾಣಿಕೆ"
    )

    val acceptProbeKeywords: List<String> = acceptKeywords.toList() +
        acceptKeywords.map { keyword ->
            keyword.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
            }
        }

    private val hardBlockedActionPhrases = setOf(
        "go to",
        "start ride",
        "navigate",
        "arrived",
        "arrive",
        "call rider",
        "chat",
        "cancel ride",
        "online",
        "offline"
    )

    private val swipeKeywords = setOf("swipe", "slide", "drag", "खींच", "ಸ್ಲೈಡ್")

    fun isAcceptText(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        if (!acceptKeywords.any { keyword -> normalized.contains(keyword) }) return false
        return hardBlockedActionPhrases.none { blocked -> normalized.contains(blocked) }
    }

    fun isSwipeText(text: String): Boolean {
        val normalized = text.lowercase()
        return swipeKeywords.any { normalized.contains(it.lowercase()) }
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
