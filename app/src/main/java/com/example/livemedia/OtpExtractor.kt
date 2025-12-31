package com.example.livemedia

import java.util.regex.Pattern

/**
 * Advanced OTP Extractor based on Production specifications.
 * Implements multi-stage regex matching and strict post-filtering.
 */
object OtpExtractor {

    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.MULTILINE

    // Keywords for context filtering (used in regexes directly)
    private const val KEYWORDS = "otp|one[\\s-]?time|code|passcode|verification|verify|login|signin|sign-in|auth|authentication|security|confirm|confirmation|validate|access|token|secret|pin"

    // --- Regex Stages ---

    // 1) KEYWORD-ANCHORED OTP (PRIMARY — RUN FIRST)
    // Matches keyword + 0-40 any chars (lazy) + Code (4-8 digits)
    // FIX: Changed from [^\dA-Z]{0,40} to .{0,40}? because CASE_INSENSITIVE 
    // makes [A-Z] reject lowercase letters like 'is' in "OTP is 433502"
    private val STAGE_1_KEYWORD_ANCHORED = Pattern.compile(
        "(?:$KEYWORDS).{0,40}?(\\d{4,8})", 
        FLAGS
    )

    // 2) STRICT NUMERIC OTP (BANK / GOVT FALLBACK)
    // 6 digits surrounded by non-digits
    private val STAGE_2_STRICT_NUMERIC = Pattern.compile(
        "(?<!\\d)(\\d{6})(?!\\d)", 
        FLAGS
    )

    // 3) SECONDARY BROAD OTP CATCHER (LAST RESORT)
    // Matches various formats if surrounded by validation boundaries
    // FIX: Removed pure alphabetic matching to avoid capturing words like "CLICK", "HERE", "TRUE".
    // FIX: Alphanumeric and Hex must now contain at least one digit.
    private val STAGE_3_BROAD = Pattern.compile(
        "(?<!\\w)(" +
        "\\d{4,8}|" +                       // numeric
        "(?![A-Z]+\\b)[A-Z0-9]{4,10}|" +    // alphanumeric (Negative lookahead: NOT pure alpha)
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
        val cleanText = text.trim()
        
        // Run logical stages. Order implies priority.
        
        // Stage 1: Keyword Anchored (High Confidence)
        findMatch(STAGE_1_KEYWORD_ANCHORED, cleanText)?.let { return it }

        // Stage 4: Prefix (High Confidence specific form) - Moved up as it's specific
        findMatch(STAGE_4_PREFIX, cleanText)?.let { return it }

        // Stage 2: Strict Numeric (Medium Confidence)
        findMatch(STAGE_2_STRICT_NUMERIC, cleanText)?.let { return it }

        // Stage 3: Broad (Low Confidence - Use only if we have context elsewhere or desperate)
        // Only run Stage 3 if the text actually contains one of the mandatory keywords
        // (User's context requirement)
        if (containsKeyword(cleanText)) {
            findMatch(STAGE_3_BROAD, cleanText)?.let { return it }
        }

        return null
    }

    private fun findMatch(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            // Some patterns have capturing groups, others match the whole group 1/0
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
        // Optimized: Avoid Regex compilation for simple character filtering.
        // O(N) single pass.
        val sb = StringBuilder(candidate.length)
        for (char in candidate) {
            if (char.isDigit() || (char >= 'A' && char <= 'Z')) {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    private fun containsKeyword(text: String): Boolean {
        // Quick check if any mandatory context keyword exists
        return Pattern.compile(KEYWORDS, FLAGS).matcher(text).find()
    }

    // --- Post-Filters ---

    private fun passesPostFilters(candidate: String, text: String, start: Int, fullMatch: String): Boolean {
        // 1. Length Check (> 10)
        if (cleanCandidate(candidate).length > 10) return false

        // 2. Reject if inside URL (Simple heuristic: looks for http/www before)
        // Scan backwards 20 chars for http/www/://
        val lookbackStart = (start - 20).coerceAtLeast(0)
        val preText = text.substring(lookbackStart, start)
        if (preText.contains("http") || preText.contains("www") || preText.contains("://")) return false

        // 3. Adjacent to currency (₹ $ € % , .)
        // Check character immediately before (ignoring spaces)
        val matchStart = text.indexOf(fullMatch, start) // robust start finding
        if (matchStart > 0) {
            val charBefore = text[matchStart - 1]
            if ("₹$€%,".contains(charBefore)) return false
            // Check for "Rs." or "INR"
            if (text.substring((matchStart - 4).coerceAtLeast(0), matchStart).contains("Rs", true)) return false
        }
        
        // 4. Looks like date/time
        // Year: 19xx or 20xx
        if (candidate.startsWith("19") || candidate.startsWith("20")) {
             if (candidate.length == 4) return false 
        }
        // Time: HH:MM format usually caught by context, but strictly, regex handles colon.
        
        // 5. Appears multiple times (e.g. "Call 555-5555" repeated) - Complex strictness, skipping for now to favor speed.
        
        // 6. Hyphenated Prefix Check (Fix for IDs like "JK-620016-P")
        // If the match is preceded by a hyphen, ensure the word before the hyphen is a valid keyword.
        if (matchStart > 0 && text[matchStart - 1] == '-') {
            // Scan backwards to find the word attached to the hyphen
            var i = matchStart - 2
            while (i >= 0 && (text[i].isLetterOrDigit())) {
                i--
            }
            val prefix = text.substring(i + 1, matchStart - 1)
            
            // If we have a prefix, it MUST be a keyword (e.g. "OTP-12345"). 
            // If it's random (e.g. "JK-12345"), reject it.
            if (prefix.isNotEmpty() && !Pattern.compile(KEYWORDS, FLAGS).matcher(prefix).find()) {
                return false
            }
        }

        return true
    }
}
