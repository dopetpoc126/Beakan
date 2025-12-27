
import java.util.regex.Pattern

fun main() {
    val existingPatterns = listOf(
        Regex("(?i)code[:\\s]*(\\d{4,8})"),
        Regex("(?i)otp[:\\s]*(\\d{4,8})"),
        Regex("(?i)verification[:\\s]*(\\d{4,8})"),
        Regex("(\\d{4,8}) is your")
    )

    val testCases = listOf(
        "Your verification code is 123456" to "123456",
        "Code: 9988" to "9988",
        "OTP 45678" to "45678",
        "112233 is your authentication code" to "112233",
        "Use 554433 to log in" to "554433", // Fails current
        "G-123456 is your Google verification code" to "123456", // Fails current (G- prefix)
        "Your One Time Password is 998877" to "998877", // Fails current (Full text)
        "Your login code: 654321" to "654321", // Might work
        "Here is your code 123456" to "123456",
        "Verification: 123 456" to "123456", // Space in code
        "Your code is: 123456. Do not share." to "123456"
    )

    println("--- Testing Existing Patterns ---")
    var failures = 0
    
    testCases.forEach { (text, expected) ->
        var found: String? = null
        for (pattern in existingPatterns) {
            pattern.find(text)?.let { result ->
                found = result.groupValues[1]
                return@let
            }
        }
        
        if (found == expected) {
            println("[PASS] '$text' -> $found")
        } else {
            println("[FAIL] '$text' -> Found: $found, Expected: $expected")
            failures++
        }
    }
    
    println("\nFailures: $failures / ${testCases.size}")
}

main()
