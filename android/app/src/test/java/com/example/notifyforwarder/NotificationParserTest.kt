package com.example.notifyforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    @Test
    fun testAmountToPaiseExactConversion() {
        // Exact integer paise conversions without floating-point errors
        assertEquals(49937L, NotificationParser.parseAmountToPaise("499.37"))
        assertEquals(129950L, NotificationParser.parseAmountToPaise("1,299.50"))
        assertEquals(129950L, NotificationParser.parseAmountToPaise("1299.50"))
        assertEquals(50000L, NotificationParser.parseAmountToPaise("500.00"))
        assertEquals(50000L, NotificationParser.parseAmountToPaise("500"))
        assertEquals(50L, NotificationParser.parseAmountToPaise("0.50"))
        assertEquals(50050L, NotificationParser.parseAmountToPaise("500.5"))
        assertEquals(50005L, NotificationParser.parseAmountToPaise("500.05"))
        assertEquals(0L, NotificationParser.parseAmountToPaise(""))
    }

    @Test
    fun testParsePhonePeIncomingPayment() {
        val pkg = "com.phonepe.app"
        val title = "PhonePe: Payment Received"
        val text = "Received ₹499.37 from Sivakumar (UPI Ref 123456789012)"
        val postTime = 1600000000000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "PhonePe")

        assertTrue(record.isPayment)
        assertEquals("INCOMING", record.classification)
        assertEquals("₹499.37", record.amount)
        assertEquals(49937L, record.amountPaise)
        assertEquals("123456789012", record.utr)
        assertEquals("Sivakumar", record.sender)
        assertEquals("com.phonepe.app", record.packageName)
        assertEquals("PhonePe", record.appName)
    }

    @Test
    fun testParseGPayIncomingPayment() {
        val pkg = "com.google.android.apps.nbu.paisa.user"
        val title = "Google Pay"
        val text = "Rahul Sharma sent you ₹499.37 via UPI Ref 987654321098"
        val postTime = 1600000001000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "Google Pay")

        assertTrue(record.isPayment)
        assertEquals("INCOMING", record.classification)
        assertEquals("₹499.37", record.amount)
        assertEquals(49937L, record.amountPaise)
        assertEquals("987654321098", record.utr)
    }

    @Test
    fun testParsePaytmIncomingPayment() {
        val pkg = "net.one97.paytm"
        val title = "Paytm Business"
        val text = "Payment of Rs. 1,299.50 received successfully. UPI txn id 456789123456"
        val postTime = 1600000002000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "Paytm")

        assertTrue(record.isPayment)
        assertEquals("INCOMING", record.classification)
        assertEquals("₹1299.50", record.amount)
        assertEquals(129950L, record.amountPaise)
        assertEquals("456789123456", record.utr)
    }

    @Test
    fun testParseBankSmsCreditedAlert() {
        val pkg = "com.sbi.lotusintouch"
        val title = "SBI Alert"
        val text = "Dear Customer, A/c *5678 credited with INR 499.37 on 06-Oct-26 by UPI/123456789012/Ramesh"
        val postTime = 1600000003000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "YONO SBI")

        assertTrue(record.isPayment)
        assertEquals("INCOMING", record.classification)
        assertEquals("₹499.37", record.amount)
        assertEquals(49937L, record.amountPaise)
        assertEquals("123456789012", record.utr)
    }

    @Test
    fun testParseBharatPeIncomingAlert() {
        val pkg = "com.bharatpe.app"
        val title = "BharatPe"
        val text = "₹499.37 received on BharatPe QR from Anitha"
        val postTime = 1600000004000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "BharatPe")

        assertTrue(record.isPayment)
        assertEquals("INCOMING", record.classification)
        assertEquals("₹499.37", record.amount)
        assertEquals(49937L, record.amountPaise)
        assertEquals("Anitha", record.sender)
    }

    @Test
    fun testParseOutgoingDebitAlertIgnored() {
        val pkg = "com.snapwork.hdfc"
        val title = "HDFC Bank Alert"
        val text = "Debited ₹500.00 from A/C **1234 sent to Merchant Store Ref 987654321098"
        val postTime = 1600000005000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "HDFC Mobile")

        assertFalse(record.isPayment)
        assertEquals("OUTGOING", record.classification)
        assertEquals("₹500", record.amount)
        assertEquals(50000L, record.amountPaise)
        assertEquals("IGNORED", record.forwardingStatus)
    }

    @Test
    fun testParseSentToPaymentIgnored() {
        val pkg = "com.phonepe.app"
        val title = "PhonePe"
        val text = "Paid ₹499.37 to Grocery Shop. Transaction ID T24100612345678"
        val postTime = 1600000006000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "PhonePe")

        assertFalse(record.isPayment)
        assertEquals("OUTGOING", record.classification)
        assertEquals("IGNORED", record.forwardingStatus)
    }

    @Test
    fun testParseOtpAndSecurityNoiseIgnored() {
        val pkg = "com.axis.mobile"
        val title = "Axis Bank"
        val text = "123456 is your OTP for Axis Mobile login. Do not share with anyone."
        val postTime = 1600000007000L

        val record = NotificationParser.parseNotification(pkg, title, text, postTime, "Axis Mobile")

        assertFalse(record.isPayment)
        assertEquals("UNRELATED", record.classification)
        assertEquals("IGNORED", record.forwardingStatus)
    }

    @Test
    fun testDeduplicationIdGeneration() {
        val time1 = 1600000000000L
        val time2 = 1600000002000L // within same 10-second bucket
        val time3 = 1600000020000L // different bucket

        val id1 = NotificationParser.generateNotificationId("com.phonepe.app", "Received ₹499.37 from Sivakumar", time1)
        val id2 = NotificationParser.generateNotificationId("com.phonepe.app", "Received ₹499.37 from Sivakumar", time2)
        val id3 = NotificationParser.generateNotificationId("com.phonepe.app", "Received ₹499.37 from Sivakumar", time3)

        assertEquals("Same notification within bucket should have matching deduplication id", id1, id2)
        assertNotEquals("Different bucket should have distinct id", id1, id3)
    }
}
