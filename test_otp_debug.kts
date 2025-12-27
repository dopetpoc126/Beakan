import java.util.regex.Pattern

/**
 * OTP Extractor Debug Script
 * FIXED: Stage 1 now uses .{0,40}? instead of [^\dA-Z]{0,40}
 */
object OtpExtractorStub {
    const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
    
    const val KEYWORDS = "otp|one[\\s-]?time|code|passcode|verification|verify|login|signin|sign-in|auth|authentication|security|confirm|confirmation|validate|access|token|secret|pin"

    // Stage 1: Keyword + lazy 0-40 any chars + 4-8 digits
    // FIXED: .{0,40}? instead of [^\dA-Z]{0,40}
    val STAGE_1 = Pattern.compile("(?:$KEYWORDS).{0,40}?(\\d{4,8})", FLAGS)
    
    // Stage 2: Strict 6-digit
    val STAGE_2 = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)", FLAGS)
    
    // Stage 3: Broad
    val STAGE_3 = Pattern.compile("(?<!\\w)(\\d{4,8}|[A-Z0-9]{4,10})(?!\\w)", FLAGS)
    
    // Stage 4: Prefix
    val STAGE_4 = Pattern.compile("(?:otp|code|pin|pass)\\s*[:\\-]?\\s*(\\d{4,8})", FLAGS)

    fun extract(text: String): String? {
        val cleanText = text.trim()
        
        findMatch(STAGE_1, cleanText, "STAGE_1")?.let { return it }
        findMatch(STAGE_4, cleanText, "STAGE_4")?.let { return it }
        findMatch(STAGE_2, cleanText, "STAGE_2")?.let { return it }
        
        if (Pattern.compile(KEYWORDS, FLAGS).matcher(cleanText).find()) {
            findMatch(STAGE_3, cleanText, "STAGE_3")?.let { return it }
        }
        
        return null
    }

    fun findMatch(pattern: Pattern, text: String, stageName: String): String? {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val candidate = if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
            if (candidate != null) {
                return "[$stageName] ${candidate.replace(Regex("[^A-Z0-9]"), "")}"
            }
        }
        return null
    }
}

// --- Test Cases ---
val cases = listOf(
    // The Zepto case that was failing!
    "Your OTP is 433502. Use this to verify your mobile number on Zepto. Valid for 5 minutes. BsISnWdUg0y",
    "Your secret code is 123456",
    "Your verification code for Instagram is 445566",
    "G-456123 is your Google verification code",
    "Use 5544 to login",
    "PIN: 9988"
)

println("=== OTP Extractor Debug (FIXED) ===\n")
cases.forEach { text ->
    val result = OtpExtractorStub.extract(text) ?: "[NO MATCH]"
    println("Input: \"$text\"")
    println("Output: $result\n")
}
