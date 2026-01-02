
import java.util.regex.Pattern

// MIRROR OF PRODUCTION LOGIC FROM OtpExtractor.kt (Ensure this matches app code)
object OtpExtractor {
    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
    private const val KEYWORDS = "otp|one[\\s-]?time|code|passcode|verification|verify|login|signin|sign-in|auth|authentication|security|confirm|confirmation|validate|access|token|secret|pin"

    // Keywords that indicate a promo/marketing message (to avoid false positives)
    private const val EXCLUDED_KEYWORDS = "checkout|off|discount|sale|deal|coupon|flat|upto|cashback|save|offer"

    // 1) KEYWORD-ANCHORED OTP
    private val STAGE_1_KEYWORD_ANCHORED = Pattern.compile("(?:$KEYWORDS).{0,40}?(\\d{4,8})", FLAGS)

    // 2) STRICT NUMERIC OTP
    private val STAGE_2_STRICT_NUMERIC = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)", FLAGS)

    // 3) SECONDARY BROAD OTP CATCHER
    private val STAGE_3_BROAD = Pattern.compile(
        "(?<!\\w)(" +
        "\\d{4,8}|" +                       
        "(?![A-Z]+\\b)[A-Z0-9]{4,10}|" +    
        "\\d{3}[-\\s]\\d{3}|" +             
        "\\d{2}[-\\s]\\d{2}[-\\s]\\d{2}|" + 
        "(?:\\d\\s){3,7}\\d" +              
        ")(?!\\w)", 
        FLAGS
    )

    // 4) PREFIX FORM
    private val STAGE_4_PREFIX = Pattern.compile("(?:otp|code|pin|pass)\\s*[:\\-]?\\s*(\\d{4,8})", FLAGS)

    fun extract(text: String): String? {
        try {
            val cleanText = text.trim()
            findMatch(STAGE_1_KEYWORD_ANCHORED, cleanText)?.let { return it }
            findMatch(STAGE_4_PREFIX, cleanText)?.let { return it }
            findMatch(STAGE_2_STRICT_NUMERIC, cleanText)?.let { return it }
            if (containsKeyword(cleanText)) {
                findMatch(STAGE_3_BROAD, cleanText)?.let { return it }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun findMatch(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val candidate = if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group(0)
            val fullMatch = matcher.group(0)
            val start = matcher.start()
            if (candidate != null && passesPostFilters(candidate, text, start, fullMatch)) {
                return cleanCandidate(candidate)
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
        
        // Hyphenated Prefix Check (WITH FIX)
        if (matchStart > 0 && text[matchStart - 1] == '-') {
            var i = matchStart - 2
            while (i >= 0 && (text[i].isLetterOrDigit())) {
                i--
            }
            val prefix = text.substring(i + 1, matchStart - 1)
            
            // EXCEPTION: Allow single letters (e.g. "G-12345" for Google)
            if (prefix.isNotEmpty() && prefix.length > 1 && !Pattern.compile(KEYWORDS, FLAGS).matcher(prefix).find()) {
                return false
            }
        }

        // 7. Promo/Marketing Filter
        // If the text contains strong marketing keywords, assume it's a promo code, not an OTP.
        if (Pattern.compile(EXCLUDED_KEYWORDS, FLAGS).matcher(text).find()) {
            return false
        }

        return true
    }
}

data class TestCase(
    val id: String,
    val contactName: String,
    val senderNumber: String,
    val message: String,
    val expectedOtp: String?,
    val shouldExtract: Boolean,
    val failureRisk: String
)

fun main() {
    val testCases = listOf(
        TestCase("TC001", "AX-AMZN", "VM-AMZN", "Your Amazon OTP is 347921. Do not share it with anyone.", "347921", true, "None"),
        TestCase("TC002", "ICICI Bank", "+91 9823456789", "ICICI Bank: OTP for net banking login is 829104. Valid for 3 mins.", "829104", true, "Brand + number combo"),
        TestCase("TC003", "Unknown", "VK-HDFCBK", "OTP: 5503. Do not share.", "5503", true, "Short OTP"),
        TestCase("TC004", "Flipkart", "VK-FLIPKT", "Use 998877 as your Flipkart verification code.", "998877", true, "Wording variation"),
        TestCase("TC005", "SBI", "VM-SBIOTP", "Dear customer, your OTP is: 912345 for txn of Rs. 4,500.", "912345", true, "Currency numbers"),
        TestCase("TC006", "Airtel", "+91 9876543210", "Airtel: 121212 is your OTP to verify mobile number.", "121212", true, "Repeating digits"),
        TestCase("TC007", "Google", "VK-GOOGLE", "G-245901 is your Google verification code.", "245901", true, "Prefixed character"),
        TestCase("TC008", "WhatsApp", "WhatsApp", "Your WhatsApp code: 432-891. Do not share.", "432891", true, "Hyphen separated"),
        TestCase("TC009", "Swiggy", "VK-SWIGGY", "Login OTP 889900. Valid for 10 minutes. Order #55321.", "889900", true, "Order ID present"),
        TestCase("TC010", "Zomato", "VM-ZOMATO", "Your Zomato OTP is 102030 for login.", "102030", true, "Sequential digits"),
        TestCase("TC011", "Unknown", "+1 415 992 1888", "Use code 778899 to verify your account.", "778899", true, "International number"),
        TestCase("TC012", "HDFC Bank", "VK-HDFCBK", "Txn alert: Rs.2500 debited. Avl bal Rs.12000.", null, false, "False positive"),
        TestCase("TC013", "Amazon", "VK-AMZN", "Your OTP is one two three four five six.", null, false, "Spelled numbers"),
        TestCase("TC014", "PhonePe", "VK-PHONEP", "OTP 445566 is required to approve ₹999 payment.", "445566", true, "Unicode currency symbol"),
        TestCase("TC015", "My Friend Rahul", "+91 9000000000", "Bro OTP is 334455 send fast", null, false, "Personal contact spoof"),
        TestCase("TC016", "Unknown", "AD-SECURE", "Your one-time password (OTP) for login is: <b>556677</b>", "556677", true, "HTML formatting"),
        TestCase("TC017", "IRCTC", "VM-IRCTC", "OTP 987654 for ticket booking. Ref 1234567890.", "987654", true, "Long reference number"),
        TestCase("TC018", "Unknown", "AX-BANK", "Your code is 12345678 valid for 1 min.", "12345678", true, "8-digit OTP"),
        TestCase("TC019", "Google", "VK-GOOGLE", "Use 123456 to sign in. This code expires in 2 minutes.", "123456", true, "Generic phrasing"),
        TestCase("TC020", "Unknown", "VK-PROMO", "Get flat 50% OFF. Use code 445566 at checkout!", null, false, "Promo code confusion")
    )

    println("--- Comprehensive OTP Validation Test Suite ---")
    println("%-6s | %-12s | %-40s | %-10s | %-10s | %-8s".format("ID", "Expected", "Risk", "Result", "Status", "Message"))
    println("-".repeat(100))

    var passed = 0
    var failed = 0

    testCases.forEach { tc ->
        val result = OtpExtractor.extract(tc.message)
        val isPass = result == tc.expectedOtp
        val status = if (isPass) "PASS" else "FAIL"
        
        if (isPass) passed++ else failed++

        val msgTrunc = if (tc.message.length > 30) tc.message.take(27) + "..." else tc.message
        
        println("%-6s | %-12s | %-40s | %-10s | %-10s | %s".format(
            tc.id, 
            tc.expectedOtp ?: "null", 
            tc.failureRisk, 
            result ?: "null", 
            status,
            msgTrunc
        ))
    }

    println("-".repeat(100))
    println("Total: ${testCases.size} | Passed: $passed | Failed: $failed")
}

main()
