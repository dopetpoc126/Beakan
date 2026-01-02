
import java.util.regex.Pattern

object OtpExtractor {

    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.MULTILINE

    // Keywords for context filtering (used in regexes directly)
    private const val KEYWORDS = "otp|one[\\s-]?time|code|passcode|verification|verify|login|signin|sign-in|auth|authentication|security|confirm|confirmation|validate|access|token|secret|pin"

    // --- Regex Stages ---

    // 1) KEYWORD-ANCHORED OTP (PRIMARY — RUN FIRST)
    private val STAGE_1_KEYWORD_ANCHORED = Pattern.compile(
        "(?:$KEYWORDS).{0,40}?(\\d{4,8})", 
        FLAGS
    )

    // 2) STRICT NUMERIC OTP (BANK / GOVT FALLBACK)
    private val STAGE_2_STRICT_NUMERIC = Pattern.compile(
        "(?<!\\d)(\\d{6})(?!\\d)", 
        FLAGS
    )

    // 3) SECONDARY BROAD OTP CATCHER (LAST RESORT)
    private val STAGE_3_BROAD = Pattern.compile(
        "(?<!\\w)(" +
        "\\d{4,8}|" +                       // numeric
        "(?![A-Z]+\\b)[A-Z0-9]{4,10}|" +    // alphanumeric
        "\\d{3}[-\\s]\\d{3}|" +             // grouped numeric (123-456)
        "\\d{2}[-\\s]\\d{2}[-\\s]\\d{2}|" + // grouped (12-34-56)
        "(?:\\d\\s){3,7}\\d" +              // spaced digits (1 2 3 4 5 6)
        ")(?!\\w)", 
        FLAGS
    )

    // 4) OPTIONAL KEYWORD + NUMERIC PREFIX FORM
    private val STAGE_4_PREFIX = Pattern.compile(
        "(?:otp|code|pin|pass)\\s*[:\\-]?\\s*(\\d{4,8})", 
        FLAGS
    )

    // --- Extraction Logic ---

    fun extract(text: String): String? {
        try {
            val cleanText = text.trim()
            
            // Log matching attempts for debugging
            println("Analyzing: '$cleanText'")

            if (findMatch(STAGE_1_KEYWORD_ANCHORED, cleanText, "STAGE 1")?.let { return it } != null) return null
            if (findMatch(STAGE_4_PREFIX, cleanText, "STAGE 4")?.let { return it } != null) return null
            if (findMatch(STAGE_2_STRICT_NUMERIC, cleanText, "STAGE 2")?.let { return it } != null) return null
            
            if (containsKeyword(cleanText)) {
                if (findMatch(STAGE_3_BROAD, cleanText, "STAGE 3")?.let { return it } != null) return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun findMatch(pattern: Pattern, text: String, stageName: String): String? {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val candidate = if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
            val fullMatch = matcher.group(0)
            val start = matcher.start()
            
            println("  [$stageName] Match found: '$candidate' (Full: '$fullMatch')")

            if (candidate != null && passesPostFilters(candidate, text, start, fullMatch)) {
                println("    -> PASSED filters: $candidate")
                return cleanCandidate(candidate)
            } else {
                println("    -> FAILED filters: $candidate")
            }
        }
        return null
    }

    private fun cleanCandidate(candidate: String): String {
        val sb = StringBuilder(candidate.length)
        for (char in candidate) {
            if (char.isDigit() || (char >= 'A' && char <= 'Z')) {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    private fun containsKeyword(text: String): Boolean {
        return Pattern.compile(KEYWORDS, FLAGS).matcher(text).find()
    }

    // --- Post-Filters ---

    private fun passesPostFilters(candidate: String, text: String, start: Int, fullMatch: String): Boolean {
        if (cleanCandidate(candidate).length > 10) return false

        val lookbackStart = (start - 20).coerceAtLeast(0)
        val preText = text.substring(lookbackStart, start)
        if (preText.contains("http") || preText.contains("www") || preText.contains("://")) return false

        val matchStart = text.indexOf(fullMatch, start)
        if (matchStart > 0) {
            val charBefore = text[matchStart - 1]
            if ("₹$€%,".contains(charBefore)) return false
            if (text.substring((matchStart - 4).coerceAtLeast(0), matchStart).contains("Rs", true)) return false
        }
        
        if (candidate.startsWith("19") || candidate.startsWith("20")) {
             if (candidate.length == 4) return false 
        }
        
        // Hyphenated Prefix Check
        if (matchStart > 0 && text[matchStart - 1] == '-') {
            var i = matchStart - 2
            while (i >= 0 && (text[i].isLetterOrDigit())) {
                i--
            }
            val prefix = text.substring(i + 1, matchStart - 1)
            
            println("    [Filter] Hyphen Prefix detected: '$prefix'")
            
            // If we have a prefix, it MUST be a keyword (e.g. "OTP-12345"). 
            // EXCEPTION: Allow single letters (e.g. "G-12345" for Google)
            if (prefix.isNotEmpty() && prefix.length > 1 && !Pattern.compile(KEYWORDS, FLAGS).matcher(prefix).find()) {
                println("    [Filter] Rejecting because prefix '$prefix' is not a keyword")
                return false
            }
        }

        return true
    }
}

fun main() {
    val sender = "59029411"
    val message = "G-016292 is your Google verification code. Don't share your code with anyone."
    
    // Simulate what the notification listener might produce
    val combinedText = "$sender $message"
    
    val result = OtpExtractor.extract(combinedText)
    println("\nFinal Result: $result")
    
    if (result == "016292") {
        println("SUCCESS: Extracted generic OTP")
    } else if (result == "59029411") {
        println("FAILURE: Extracted Sender Number")
    } else {
        println("FAILURE: Unexpected result '$result'")
    }
}

main()
