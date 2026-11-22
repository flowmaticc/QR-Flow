package net.hilson.qrieux.util

sealed class QrContentType {
    data class Url(val url: String) : QrContentType()
    data class Email(val email: String) : QrContentType()
    data class Phone(val phone: String) : QrContentType()
    data class Wifi(val ssid: String, val password: String, val authType: String, val hidden: Boolean) : QrContentType()
    data class Contact(
        val fullName: String,
        val phone: String,
        val email: String,
        val organization: String,
        val rawVCard: String,
        val url: String = ""
    ) : QrContentType()
    data class Sms(val number: String, val body: String) : QrContentType()
    data class Geo(val latitude: Double, val longitude: Double, val query: String, val raw: String) : QrContentType()
    data class Event(
        val summary: String,
        val start: String,
        val end: String,
        val location: String,
        val description: String,
        val raw: String
    ) : QrContentType()
    /** A link that opens another app or a non-web resource (market:, otpauth:, bitcoin:, intent:, javascript:…). */
    data class OtherUri(val uri: String, val scheme: String) : QrContentType()
    data class Text(val text: String) : QrContentType()

    /** Human readable summary of the payload, shared by every result screen. */
    fun displayText(): String = when (this) {
        is Url -> url
        is Email -> email
        is Phone -> phone
        is Wifi -> ssid
        is Contact -> buildString {
            append(fullName)
            if (phone.isNotEmpty()) append("\n$phone")
            if (email.isNotEmpty()) append("\n$email")
            if (organization.isNotEmpty()) append("\n$organization")
            if (url.isNotEmpty()) append("\n$url")
        }
        is Sms -> if (body.isEmpty()) number else "$number\n\n$body"
        is Geo -> buildString {
            append("${latitude}, ${longitude}")
            if (query.isNotEmpty()) append("\n$query")
        }
        is Event -> buildString {
            append(summary.ifEmpty { "—" })
            val when_ = listOf(formatIcalDate(start), formatIcalDate(end)).filter { it.isNotEmpty() }
            if (when_.isNotEmpty()) append("\n${when_.joinToString(" → ")}")
            if (location.isNotEmpty()) append("\n$location")
            if (description.isNotEmpty()) append("\n\n$description")
        }
        is OtherUri -> uri
        is Text -> text
    }

    /** One-line label for the history list. */
    fun shortLabel(): String = when (this) {
        is Contact -> fullName.ifEmpty { phone.ifEmpty { email } }
        is Event -> summary.ifEmpty { formatIcalDate(start) }
        else -> displayText()
    }.lineSequence().first().take(80)

    companion object {
        private val URL_PATTERN = Regex("^https?://.*", RegexOption.IGNORE_CASE)
        private val EMAIL_PATTERN = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        private val PHONE_PATTERN = Regex("^\\+?[0-9\\s\\-()]{7,}$")
        private val HIERARCHICAL_URI = Regex("^([a-zA-Z][a-zA-Z0-9+.-]{1,31})://\\S+$")
        private val SCHEME_PREFIX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]{1,31}):\\S+$")

        // Schemes that are recognised even without "//" (javascript:alert(1), bitcoin:addr…).
        private val KNOWN_OPAQUE_SCHEMES = setOf(
            "javascript", "vbscript", "data", "file", "content", "intent", "about", "blob",
            "market", "otpauth", "otpauth-migration", "bitcoin", "ethereum", "litecoin", "monero",
            "dogecoin", "lightning", "bitcoincash", "upi", "magnet", "whatsapp", "tg", "spotify",
            "skype", "facetime", "zoommtg", "mms", "android-app", "chrome", "ms-settings", "ftp",
            "sftp", "ws", "wss", "xmpp", "matrix", "signal"
        )

        fun fromRawValue(raw: String): QrContentType {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCard(trimmed) ?: Text(trimmed)
                trimmed.startsWith("MECARD:", ignoreCase = true) -> parseMeCard(trimmed) ?: Text(trimmed)
                trimmed.startsWith("BEGIN:VCALENDAR", ignoreCase = true) ||
                    trimmed.startsWith("BEGIN:VEVENT", ignoreCase = true) -> parseEvent(trimmed) ?: Text(trimmed)
                trimmed.startsWith("WIFI:", ignoreCase = true) -> parseWifi(trimmed) ?: Text(trimmed)
                URL_PATTERN.matches(trimmed) -> Url(trimmed)
                trimmed.startsWith("mailto:", ignoreCase = true) -> Email(trimmed.substring(7))
                trimmed.startsWith("MATMSG:", ignoreCase = true) -> parseMatMsg(trimmed) ?: Text(trimmed)
                EMAIL_PATTERN.matches(trimmed) -> Email(trimmed)
                trimmed.startsWith("tel:", ignoreCase = true) -> Phone(trimmed.substring(4))
                trimmed.startsWith("smsto:", ignoreCase = true) ||
                    trimmed.startsWith("sms:", ignoreCase = true) ||
                    trimmed.startsWith("mmsto:", ignoreCase = true) -> parseSms(trimmed) ?: Text(trimmed)
                trimmed.startsWith("geo:", ignoreCase = true) -> parseGeo(trimmed) ?: Text(trimmed)
                PHONE_PATTERN.matches(trimmed) -> Phone(trimmed)
                else -> parseOtherUri(trimmed) ?: Text(trimmed)
            }
        }

        private fun parseOtherUri(trimmed: String): OtherUri? {
            if (trimmed.contains('\n')) return null
            HIERARCHICAL_URI.matchEntire(trimmed)?.let {
                return OtherUri(trimmed, it.groupValues[1].lowercase())
            }
            val scheme = SCHEME_PREFIX.matchEntire(trimmed)?.groupValues?.get(1)?.lowercase() ?: return null
            return if (scheme in KNOWN_OPAQUE_SCHEMES) OtherUri(trimmed, scheme) else null
        }

        private fun parseVCard(raw: String): Contact? {
            var fullName = ""
            var phone = ""
            var email = ""
            var org = ""
            var url = ""
            // Handle both \r\n and \n line endings
            val lines = raw.replace("\r\n", "\n").split("\n")
            for (line in lines) {
                val upper = line.uppercase()
                when {
                    upper.startsWith("FN:") || upper.startsWith("FN;") -> {
                        fullName = extractValue(line)
                    }
                    (upper.startsWith("TEL:") || upper.startsWith("TEL;")) && phone.isEmpty() -> {
                        phone = extractValue(line)
                    }
                    (upper.startsWith("EMAIL:") || upper.startsWith("EMAIL;")) && email.isEmpty() -> {
                        email = extractValue(line)
                    }
                    upper.startsWith("ORG:") || upper.startsWith("ORG;") -> {
                        org = extractValue(line)
                    }
                    (upper.startsWith("URL:") || upper.startsWith("URL;")) && url.isEmpty() -> {
                        url = extractValue(line)
                    }
                }
            }
            if (fullName.isEmpty() && phone.isEmpty() && email.isEmpty()) return null
            return Contact(fullName, phone, email, org, raw, url)
        }

        // MECARD:N:Doe,John;TEL:123;EMAIL:a@b.c;URL:https://x;ORG:Acme;;
        private fun parseMeCard(raw: String): Contact? {
            val fields = splitUnescaped(raw.substring(7).removeSuffix(";;").removeSuffix(";"), ';')
            var name = ""
            var phone = ""
            var email = ""
            var org = ""
            var url = ""
            for (field in fields) {
                val idx = field.indexOf(':')
                if (idx < 0) continue
                val value = unescape(field.substring(idx + 1))
                when (field.substring(0, idx).uppercase()) {
                    "N" -> name = value.split(',').map { it.trim() }.reversed().joinToString(" ").trim()
                    "TEL" -> if (phone.isEmpty()) phone = value
                    "EMAIL" -> if (email.isEmpty()) email = value
                    "ORG" -> org = value
                    "URL" -> if (url.isEmpty()) url = value
                }
            }
            if (name.isEmpty() && phone.isEmpty() && email.isEmpty()) return null
            val vCard = buildString {
                append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
                if (name.isNotEmpty()) append("FN:$name\r\n")
                if (phone.isNotEmpty()) append("TEL:$phone\r\n")
                if (email.isNotEmpty()) append("EMAIL:$email\r\n")
                if (org.isNotEmpty()) append("ORG:$org\r\n")
                if (url.isNotEmpty()) append("URL:$url\r\n")
                append("END:VCARD")
            }
            return Contact(name, phone, email, org, vCard, url)
        }

        // MATMSG:TO:a@b.c;SUB:Subject;BODY:Body;;
        private fun parseMatMsg(raw: String): Email? {
            val fields = splitUnescaped(raw.substring(7).removeSuffix(";;").removeSuffix(";"), ';')
            for (field in fields) {
                if (field.uppercase().startsWith("TO:")) {
                    val to = unescape(field.substring(3)).trim()
                    if (to.isNotEmpty()) return Email(to)
                }
            }
            return null
        }

        // SMSTO:+34600000000:Hello   ·   sms:+34600000000?body=Hello   ·   sms:+34600000000
        private fun parseSms(raw: String): Sms? {
            val colon = raw.indexOf(':')
            val rest = raw.substring(colon + 1)
            val isSmsTo = raw.substring(0, colon).endsWith("to", ignoreCase = true)
            val (number, body) = if (isSmsTo) {
                val sep = rest.indexOf(':')
                if (sep >= 0) rest.substring(0, sep) to rest.substring(sep + 1) else rest to ""
            } else {
                val q = rest.indexOf('?')
                if (q >= 0) {
                    val params = rest.substring(q + 1).split('&')
                    val b = params.firstOrNull { it.startsWith("body=", ignoreCase = true) }
                        ?.substring(5)?.let { percentDecode(it) } ?: ""
                    rest.substring(0, q) to b
                } else rest to ""
            }
            val cleanNumber = number.trim()
            if (cleanNumber.isEmpty()) return null
            return Sms(cleanNumber, body)
        }

        // geo:40.4168,-3.7038?q=Puerta+del+Sol   ·   geo:40.4168,-3.7038,650
        private fun parseGeo(raw: String): Geo? {
            val body = raw.substring(4)
            val q = body.indexOf('?')
            val coords = (if (q >= 0) body.substring(0, q) else body).split(';')[0].split(',')
            if (coords.size < 2) return null
            val lat = coords[0].trim().toDoubleOrNull() ?: return null
            val lon = coords[1].trim().toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            val query = if (q >= 0) {
                body.substring(q + 1).split('&').firstOrNull { it.startsWith("q=", ignoreCase = true) }
                    ?.substring(2)?.replace('+', ' ')?.let { percentDecode(it) } ?: ""
            } else ""
            return Geo(lat, lon, query, raw)
        }

        private fun parseEvent(raw: String): Event? {
            val lines = unfoldIcal(raw)
            var inEvent = false
            var summary = ""
            var start = ""
            var end = ""
            var location = ""
            var description = ""
            for (line in lines) {
                val upper = line.uppercase()
                when {
                    upper.startsWith("BEGIN:VEVENT") -> inEvent = true
                    upper.startsWith("END:VEVENT") -> break
                    !inEvent -> {}
                    upper.startsWith("SUMMARY") -> summary = icalUnescape(extractValue(line))
                    upper.startsWith("DTSTART") -> start = extractValue(line)
                    upper.startsWith("DTEND") -> end = extractValue(line)
                    upper.startsWith("LOCATION") -> location = icalUnescape(extractValue(line))
                    upper.startsWith("DESCRIPTION") -> description = icalUnescape(extractValue(line))
                }
            }
            if (summary.isEmpty() && start.isEmpty()) return null
            return Event(summary, start, end, location, description, raw)
        }

        private fun unfoldIcal(raw: String): List<String> {
            val out = mutableListOf<String>()
            for (line in raw.replace("\r\n", "\n").split("\n")) {
                if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                    out[out.lastIndex] = out.last() + line.substring(1)
                } else out.add(line)
            }
            return out
        }

        private fun icalUnescape(s: String): String =
            s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

        /** 20261122T100000Z → 2026-11-22 10:00 UTC · 20261122 → 2026-11-22 */
        fun formatIcalDate(value: String): String {
            val v = value.trim()
            val m = Regex("^(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})?(Z)?)?$").matchEntire(v) ?: return v
            val g = m.groupValues
            return buildString {
                append("${g[1]}-${g[2]}-${g[3]}")
                if (g[4].isNotEmpty()) append(" ${g[4]}:${g[5]}")
                if (g[7].isNotEmpty()) append(" UTC")
            }
        }

        private fun extractValue(line: String): String {
            val colonIdx = line.indexOf(':')
            return if (colonIdx >= 0) line.substring(colonIdx + 1).trim() else ""
        }

        // Format: WIFI:T:WPA;S:MyNetwork;P:MyPassword;H:true;;
        private fun parseWifi(raw: String): Wifi? {
            val body = raw.substring(5).removeSuffix(";;").removeSuffix(";")
            val fields = splitUnescaped(body, ';')
            var authType = ""
            var ssid = ""
            var password = ""
            var hidden = false
            for (field in fields) {
                val colonIdx = field.indexOf(':')
                if (colonIdx < 0) continue
                val key = field.substring(0, colonIdx)
                val value = unescape(field.substring(colonIdx + 1))
                when (key.uppercase()) {
                    "T" -> authType = value.uppercase()
                    "S" -> ssid = value
                    "P" -> password = value
                    "H" -> hidden = value.equals("true", ignoreCase = true)
                }
            }
            if (ssid.isEmpty()) return null
            return Wifi(ssid, password, authType, hidden)
        }

        /** Builds a WIFI: payload; used to store a network in history without its password. */
        fun buildWifiPayload(ssid: String, password: String, authType: String, hidden: Boolean): String = buildString {
            append("WIFI:")
            if (authType.isNotEmpty()) append("T:${escapeWifi(authType)};")
            append("S:${escapeWifi(ssid)};")
            if (password.isNotEmpty()) append("P:${escapeWifi(password)};")
            if (hidden) append("H:true;")
            append(";")
        }

        private fun escapeWifi(s: String): String = buildString {
            for (c in s) {
                if (c in "\\;,:\"") append('\\')
                append(c)
            }
        }

        private fun splitUnescaped(s: String, delimiter: Char): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    current.append(s[i])
                    current.append(s[i + 1])
                    i += 2
                } else if (s[i] == delimiter) {
                    parts.add(current.toString())
                    current.clear()
                    i++
                } else {
                    current.append(s[i])
                    i++
                }
            }
            if (current.isNotEmpty()) parts.add(current.toString())
            return parts
        }

        private fun unescape(s: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    sb.append(s[i + 1])
                    i += 2
                } else {
                    sb.append(s[i])
                    i++
                }
            }
            return sb.toString()
        }

        fun percentDecode(s: String): String {
            val bytes = ArrayList<Byte>(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 <= s.lastIndex) {
                    val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes.add(hex.toByte()); i += 3; continue
                    }
                }
                c.toString().encodeToByteArray().forEach { bytes.add(it) }
                i++
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}
