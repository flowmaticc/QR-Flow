package net.hilson.qrieux.util

import net.hilson.qrieux.history.redactForHistory
import net.hilson.qrieux.history.HistoryEntry
import net.hilson.qrieux.history.HistoryEntryType
import net.hilson.qrieux.history.pruneOlderThan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QrContentTypeExtendedTest {

    @Test fun smsTo() {
        val c = assertIs<QrContentType.Sms>(QrContentType.fromRawValue("SMSTO:+34600111222:Hola que tal"))
        assertEquals("+34600111222", c.number)
        assertEquals("Hola que tal", c.body)
    }

    @Test fun smsUri() {
        val c = assertIs<QrContentType.Sms>(QrContentType.fromRawValue("sms:+34600111222?body=Hola%20mundo"))
        assertEquals("+34600111222", c.number)
        assertEquals("Hola mundo", c.body)
    }

    @Test fun geo() {
        val c = assertIs<QrContentType.Geo>(QrContentType.fromRawValue("geo:40.4168,-3.7038?q=Puerta+del+Sol"))
        assertEquals(40.4168, c.latitude)
        assertEquals(-3.7038, c.longitude)
        assertEquals("Puerta del Sol", c.query)
    }

    @Test fun geoOutOfRangeIsText() { assertIs<QrContentType.Text>(QrContentType.fromRawValue("geo:200,10")) }

    @Test fun event() {
        val raw = "BEGIN:VEVENT\nSUMMARY:Reunión\\, equipo\nDTSTART:20261201T090000Z\nDTEND:20261201T100000Z\nLOCATION:Madrid\nEND:VEVENT"
        val c = assertIs<QrContentType.Event>(QrContentType.fromRawValue(raw))
        assertEquals("Reunión, equipo", c.summary)
        assertEquals("20261201T090000Z", c.start)
        assertEquals("Madrid", c.location)
        assertEquals("2026-12-01 09:00 UTC", QrContentType.formatIcalDate(c.start))
    }

    @Test fun meCard() {
        val c = assertIs<QrContentType.Contact>(QrContentType.fromRawValue("MECARD:N:Pérez,Ana;TEL:+34600000000;EMAIL:ana@example.com;;"))
        assertEquals("Ana Pérez", c.fullName)
        assertEquals("+34600000000", c.phone)
        kotlin.test.assertTrue(c.rawVCard.startsWith("BEGIN:VCARD"))
    }

    @Test fun matMsg() {
        val c = assertIs<QrContentType.Email>(QrContentType.fromRawValue("MATMSG:TO:info@example.com;SUB:Hola;BODY:Texto;;"))
        assertEquals("info@example.com", c.email)
    }

    @Test fun otherUri() {
        val c = assertIs<QrContentType.OtherUri>(QrContentType.fromRawValue("market://details?id=org.example"))
        assertEquals("market", c.scheme)
    }

    @Test fun colonTextStaysText() { assertIs<QrContentType.Text>(QrContentType.fromRawValue("Nota:comprar pan")) }

    @Test fun wifiRedaction() {
        val redacted = redactForHistory("WIFI:T:WPA;S:Mi;Casa;P:secreta123;H:true;;".replace("Mi;Casa", "Mi\\;Casa"))
        val parsed = assertIs<QrContentType.Wifi>(QrContentType.fromRawValue(redacted))
        assertEquals("Mi;Casa", parsed.ssid)
        assertEquals("", parsed.password)
        assertEquals("WPA", parsed.authType)
        assertEquals(true, parsed.hidden)
    }

    @Test fun nonWifiNotRedacted() = assertEquals("https://example.com", redactForHistory("https://example.com"))

    @Test fun recentDuplicate() {
        val entries = listOf(
            HistoryEntry("new", 100_000, HistoryEntryType.SCAN, "WIFI:T:WPA;S:Casa;;"),
            HistoryEntry("old", 1_000, HistoryEntryType.SCAN, "https://example.com")
        )
        val dup = net.hilson.qrieux.history.findRecentDuplicate(entries, HistoryEntryType.SCAN, "WIFI:T:WPA;S:Casa;;", 150_000)
        assertEquals("new", dup?.id)
        // Too late, different content, older entry or a created code: saved as a new entry.
        kotlin.test.assertNull(net.hilson.qrieux.history.findRecentDuplicate(entries, HistoryEntryType.SCAN, "WIFI:T:WPA;S:Casa;;", 100_000 + 3 * 60_000))
        kotlin.test.assertNull(net.hilson.qrieux.history.findRecentDuplicate(entries, HistoryEntryType.SCAN, "https://example.com", 150_000))
        kotlin.test.assertNull(net.hilson.qrieux.history.findRecentDuplicate(entries, HistoryEntryType.GENERATE, "WIFI:T:WPA;S:Casa;;", 150_000))
    }

    @Test fun prune() {
        val entries = listOf(
            HistoryEntry("a", 1_000, HistoryEntryType.SCAN, "x"),
            HistoryEntry("b", 5_000, HistoryEntryType.SCAN, "y")
        )
        assertEquals(listOf("b"), pruneOlderThan(entries, 2_000).map { it.id })
    }
}
