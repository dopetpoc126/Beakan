package com.example.livemedia

import org.junit.Test
import java.io.File
import org.junit.Assert.assertEquals

class OtpRegexTest {

    @Test
    fun testOtpCsv() {
        // Absolute path to the user's dataset
        val csvPath = "C:\\Users\\shriy\\Downloads\\realworld_style_otp_dataset_1000.csv"
        val file = File(csvPath)
        
        if (!file.exists()) {
            println("ERROR: CSV File not found at $csvPath. Please Ensure file exists.")
            return
        }

        println("Loading Dataset from: ${file.absolutePath}")
        val lines = file.readLines()
        println("Total Lines: ${lines.size}")

        var passed = 0
        var failed = 0
        var falsePositives = 0
        var missed = 0
        var total = 0

        // Start skipping header (index 1 to end)
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            
            // CSV columns: SenderID,Name,Phone,OTP,Message
            // Split by comma. Message is the last column (index 4).
            // NOTE: Simple split assumes no commas in Name/SenderID/Phone/OTP.
            // If Message has commas, 'limit' helps capture the rest.
            val parts = line.split(",", limit = 5)
            
            if (parts.size < 5) {
                println("SKIP Line ${i+1}: Invalid Format. Content: '$line'")
                continue
            }
            
            // Expected
            val expectedOtp = parts[3].trim()
            val messageText = parts[4].trim() 
            
            total++

            // Test Extraction
            val extracted = OtpExtractor.extract(messageText)

            if (extracted == expectedOtp) {
                passed++
            } else {
                failed++
                if (extracted == null) {
                    missed++
                    println("MISS [Line ${i+1}]: Expected '$expectedOtp', Got NULL. \n\tMsg: '$messageText'")
                } else {
                    falsePositives++ 
                    println("WRONG [Line ${i+1}]: Expected '$expectedOtp', Got '$extracted'. \n\tMsg: '$messageText'")
                }
            }
        }
        
        println("===================================================")
        println("TEST RESULTS (Dataset: 1000)")
        println("===================================================")
        println("TOTAL CASES: $total")
        println("PASSED:      $passed")
        println("FAILED:      $failed")
        println("  -> MISSED: $missed (Returned Null)")
        println("  -> WRONG:  $falsePositives (Returned Incorrect OTP)")
        println("===================================================")
        if (total > 0) {
            val accuracy = (passed.toFloat() / total) * 100
            println("Accuracy: ${String.format("%.2f", accuracy)}%")
        }
    }
}
