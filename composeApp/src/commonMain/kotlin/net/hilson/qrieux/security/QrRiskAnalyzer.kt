package net.hilson.qrieux.security

import net.hilson.qrieux.util.QrContentType

/**
 * Offline heuristics that flag QR codes commonly used for "quishing" (QR phishing),
 * premium-rate fraud, USSD/MMI attacks and malicious app links.
 *
 * Nothing here touches the network: every check runs on the decoded text only.
 */
enum class RiskLevel { SAFE, INFO, CAUTION, DANGER }

enum class RiskReason(val level: RiskLevel) {
    // Links
    DANGEROUS_SCHEME(RiskLevel.DANGER),
    USERINFO_TRICK(RiskLevel.DANGER),
    IP_ADDRESS_HOST(RiskLevel.DANGER),
    PUNYCODE_HOST(RiskLevel.DANGER),
    NON_ASCII_HOST(RiskLevel.DANGER),
    BRAND_IMPERSONATION(RiskLevel.DANGER),
    EXECUTABLE_DOWNLOAD(RiskLevel.DANGER),
    HIDDEN_CHARACTERS(RiskLevel.DANGER),
    ENCODED_HOST(RiskLevel.DANGER),
    INSECURE_HTTP(RiskLevel.CAUTION),
    URL_SHORTENER(RiskLevel.CAUTION),
    SUSPICIOUS_TLD(RiskLevel.CAUTION),
    UNUSUAL_PORT(RiskLevel.CAUTION),
    PHISHING_KEYWORDS(RiskLevel.CAUTION),
    EMBEDDED_REDIRECT(RiskLevel.CAUTION),
    EXCESSIVE_SUBDOMAINS(RiskLevel.CAUTION),
    APP_SCHEME(RiskLevel.CAUTION),
    CRYPTO_PAYMENT(RiskLevel.CAUTION),
    OTP_SECRET(RiskLevel.INFO),
    VERY_LONG(RiskLevel.INFO),
    TEXT_CONTAINS_LINK(RiskLevel.INFO),
    // Phone / SMS
    MMI_CODE(RiskLevel.DANGER),
    PREMIUM_NUMBER(RiskLevel.DANGER),
    SMS_SHORT_NUMBER(RiskLevel.CAUTION),
    SMS_PREFILLED_BODY(RiskLevel.INFO),
    // Wi-Fi
    OPEN_WIFI(RiskLevel.CAUTION),
    WEAK_WIFI(RiskLevel.CAUTION),
}

data class RiskReport(
    val level: RiskLevel,
    val reasons: List<RiskReason>,
    /** The domain that really receives the visit (e.g. "evil.com" for https://paypal.com@evil.com). */
    val realDomain: String? = null,
    /** Full host, when different from the registrable domain. */
    val host: String? = null
) {
    val needsConfirmation: Boolean get() = level >= RiskLevel.CAUTION

    companion object {
        val SAFE = RiskReport(RiskLevel.SAFE, emptyList())
    }
}

object QrRiskAnalyzer {

    fun analyze(content: QrContentType): RiskReport {
        val reasons = linkedSetOf<RiskReason>()
        var domain: String? = null
        var host: String? = null

        if (containsHiddenCharacters(content.displayText())) reasons += RiskReason.HIDDEN_CHARACTERS

        when (content) {
            is QrContentType.Url -> {
                val r = analyzeUrl(content.url)
                reasons += r.reasons; domain = r.realDomain; host = r.host
            }
            is QrContentType.OtherUri -> reasons += analyzeOtherUri(content)
            is QrContentType.Email -> {
                val emailDomain = content.email.substringAfter('@', "").substringBefore('?').lowercase()
                if (emailDomain.isNotEmpty()) {
                    domain = registrableDomain(emailDomain)
                    if (impersonatesBrand(emailDomain)) reasons += RiskReason.BRAND_IMPERSONATION
                    if (tldOf(emailDomain) in SUSPICIOUS_TLDS) reasons += RiskReason.SUSPICIOUS_TLD
                    if (emailDomain.split('.').any { it.startsWith("xn--") }) reasons += RiskReason.PUNYCODE_HOST
                }
            }
            is QrContentType.Phone -> reasons += analyzePhone(content.phone)
            is QrContentType.Sms -> {
                reasons += analyzePhone(content.number)
                val digits = content.number.filter { it.isDigit() }
                if (!content.number.startsWith("+") && digits.length in 3..6) reasons += RiskReason.SMS_SHORT_NUMBER
                if (content.body.isNotBlank()) reasons += RiskReason.SMS_PREFILLED_BODY
            }
            is QrContentType.Wifi -> {
                val auth = content.authType.uppercase()
                when {
                    auth == "WEP" -> reasons += RiskReason.WEAK_WIFI
                    (auth.isEmpty() || auth == "NOPASS") && content.password.isEmpty() -> reasons += RiskReason.OPEN_WIFI
                }
            }
            is QrContentType.Contact -> {
                if (content.phone.isNotEmpty()) reasons += analyzePhone(content.phone)
                if (content.url.isNotEmpty() && content.url.startsWith("http", ignoreCase = true)) {
                    val r = analyzeUrl(content.url)
                    reasons += r.reasons; domain = r.realDomain; host = r.host
                }
            }
            is QrContentType.Event -> {
                findFirstLink(content.description + " " + content.location)?.let { link ->
                    reasons += RiskReason.TEXT_CONTAINS_LINK
                    val r = analyzeUrl(link)
                    reasons += r.reasons; domain = r.realDomain; host = r.host
                }
            }
            is QrContentType.Text -> {
                findFirstLink(content.text)?.let { link ->
                    reasons += RiskReason.TEXT_CONTAINS_LINK
                    val r = analyzeUrl(link)
                    reasons += r.reasons; domain = r.realDomain; host = r.host
                }
            }
            is QrContentType.Geo -> {}
        }

        val sorted = reasons.sortedByDescending { it.level }
        val level = sorted.maxOfOrNull { it.level } ?: RiskLevel.SAFE
        return RiskReport(level, sorted, domain, host?.takeIf { it != domain })
    }

    // ---------------------------------------------------------------- URLs

    data class ParsedUrl(
        val scheme: String,
        val userInfo: String?,
        val host: String,
        val port: Int?,
        val path: String,
        val query: String
    )

    fun parseUrl(url: String): ParsedUrl? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        val rest = url.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val afterAuthority = rest.substring(authorityEnd)
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else null
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        val (host, port) = if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            val h = if (close > 0) hostPort.substring(0, close + 1) else hostPort
            val p = if (close > 0 && close + 1 < hostPort.length && hostPort[close + 1] == ':')
                hostPort.substring(close + 2).toIntOrNull() else null
            h to p
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) hostPort.substring(0, colon) to hostPort.substring(colon + 1).toIntOrNull()
            else hostPort to null
        }
        val fragmentless = afterAuthority.substringBefore('#')
        val path = fragmentless.substringBefore('?')
        val query = if (fragmentless.contains('?')) fragmentless.substringAfter('?') else ""
        return ParsedUrl(scheme, userInfo, host.lowercase().trimEnd('.'), port, path, query)
    }

    private data class UrlAnalysis(val reasons: Set<RiskReason>, val realDomain: String?, val host: String?)

    private fun analyzeUrl(url: String): UrlAnalysis {
        val reasons = linkedSetOf<RiskReason>()
        val parsed = parseUrl(url) ?: return UrlAnalysis(emptySet(), null, null)
        val host = parsed.host

        if (parsed.scheme == "http") reasons += RiskReason.INSECURE_HTTP
        if (!parsed.userInfo.isNullOrEmpty()) reasons += RiskReason.USERINFO_TRICK
        if (host.contains('%')) reasons += RiskReason.ENCODED_HOST
        if (isIpAddress(host)) reasons += RiskReason.IP_ADDRESS_HOST
        val labels = host.split('.')
        if (labels.any { it.startsWith("xn--") }) reasons += RiskReason.PUNYCODE_HOST
        if (host.any { it.code > 127 }) reasons += RiskReason.NON_ASCII_HOST

        val domain = if (isIpAddress(host)) host else registrableDomain(host)
        if (domain in SHORTENERS || host in SHORTENERS) reasons += RiskReason.URL_SHORTENER
        if (tldOf(host) in SUSPICIOUS_TLDS) reasons += RiskReason.SUSPICIOUS_TLD
        parsed.port?.let { if (it != 80 && it != 443) reasons += RiskReason.UNUSUAL_PORT }
        if (labels.size > 5) reasons += RiskReason.EXCESSIVE_SUBDOMAINS

        // A brand named in the user-info part is also an impersonation attempt (https://bbva.es@evil.top).
        if (impersonatesBrand(host) || (parsed.userInfo?.let { brandTokenIn(it.lowercase()) } != null)) {
            reasons += RiskReason.BRAND_IMPERSONATION
        }

        val hostTokens = tokens(host.substringBeforeLast('.'))
        if (!isOfficialDomain(host) && hostTokens.any { it in PHISHING_KEYWORDS }) reasons += RiskReason.PHISHING_KEYWORDS

        val lastSegment = parsed.path.substringAfterLast('/').lowercase()
        val ext = lastSegment.substringAfterLast('.', "")
        if (ext in EXECUTABLE_EXTENSIONS) reasons += RiskReason.EXECUTABLE_DOWNLOAD

        val decodedQuery = QrContentType.percentDecode(parsed.query).lowercase()
        if (Regex("(^|&)[a-z_]*=(https?:|//)").containsMatchIn(decodedQuery)) reasons += RiskReason.EMBEDDED_REDIRECT

        if (url.length > 200) reasons += RiskReason.VERY_LONG
        return UrlAnalysis(reasons, domain, host)
    }

    private fun analyzeOtherUri(content: QrContentType.OtherUri): Set<RiskReason> {
        val s = content.scheme
        return when {
            s in DANGEROUS_SCHEMES -> setOf(RiskReason.DANGEROUS_SCHEME)
            s in CRYPTO_SCHEMES -> setOf(RiskReason.CRYPTO_PAYMENT)
            s == "otpauth" || s == "otpauth-migration" -> setOf(RiskReason.OTP_SECRET)
            else -> setOf(RiskReason.APP_SCHEME)
        }
    }

    /** True when the scheme must never be launched from a QR code. */
    fun isBlockedScheme(scheme: String): Boolean = scheme.lowercase() in DANGEROUS_SCHEMES

    // ---------------------------------------------------------------- Phones

    private fun analyzePhone(raw: String): Set<RiskReason> {
        val reasons = linkedSetOf<RiskReason>()
        // tel:*21*666# would set up call forwarding; *#06#-style codes run without pressing call on some dialers.
        if (raw.contains('*') || raw.contains('#') || raw.contains("%23") || raw.contains("%2A", ignoreCase = true)) {
            reasons += RiskReason.MMI_CODE
        }
        if (isPremiumNumber(raw)) reasons += RiskReason.PREMIUM_NUMBER
        return reasons
    }

    /** Dialling would run an MMI/USSD code instead of making a call. */
    fun isMmiCode(raw: String): Boolean = RiskReason.MMI_CODE in analyzePhone(raw)

    fun isPremiumNumber(raw: String): Boolean {
        var digits = raw.filter { it.isDigit() || it == '+' }
        digits = when {
            digits.startsWith("+34") -> digits.substring(3)
            digits.startsWith("0034") -> digits.substring(4)
            digits.startsWith("+") || digits.startsWith("00") -> return false
            else -> digits
        }
        // Spanish premium-rate (tarificación adicional) ranges: 803, 806, 807, 905, 907 and 118xx directory services.
        return digits.length >= 9 && PREMIUM_PREFIXES_ES.any { digits.startsWith(it) } ||
            (digits.length == 5 && digits.startsWith("118"))
    }

    // ---------------------------------------------------------------- Helpers

    private val BIDI_AND_INVISIBLE = setOf(
        '​', '‌', '‍', '⁠', '﻿', '­',
        '‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩',
        '‎', '‏', '؜'
    )

    fun containsHiddenCharacters(s: String): Boolean = s.any { it in BIDI_AND_INVISIBLE }

    /** Makes invisible characters visible so the user sees what the code really says. */
    fun revealHiddenCharacters(s: String): String = buildString {
        for (c in s) {
            if (c in BIDI_AND_INVISIBLE) append("⟦U+${c.code.toString(16).uppercase().padStart(4, '0')}⟧") else append(c)
        }
    }

    private fun findFirstLink(text: String): String? =
        Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(text)?.value

    private fun isIpAddress(host: String): Boolean {
        if (host.startsWith("[")) return true
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) return true
        // Decimal / hex encoded IPv4 (http://3232235777, http://0xC0A80001)
        if (Regex("^(0x[0-9a-f]+|\\d{8,10})$").matches(host)) return true
        return false
    }

    private fun tldOf(host: String): String = host.substringAfterLast('.')

    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gob", "gov", "edu", "ac", "nom", "or", "ne", "go")

    fun registrableDomain(host: String): String {
        val labels = host.trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val sld = labels[labels.size - 2]
        val take = if (tld.length == 2 && sld in SECOND_LEVEL) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    private fun tokens(s: String): List<String> =
        s.lowercase().split('.', '-', '_').filter { it.isNotEmpty() }

    /** Undo the usual look-alike tricks: paypa1 → paypal, rnicrosoft → microsoft, g00gle → google. */
    private fun normalizeLookalikes(s: String): String = s
        .replace("rn", "m").replace("vv", "w")
        .replace('0', 'o').replace('1', 'l').replace('3', 'e').replace('4', 'a')
        .replace('5', 's').replace('7', 't').replace('@', 'a').replace('$', 's')

    private fun isOfficialDomain(host: String): Boolean {
        val domain = registrableDomain(host)
        return BRANDS.values.any { officials -> officials.any { domain == it || host.endsWith(".$it") || host == it } }
    }

    private val BRAND_AFFIXES = setOf(
        "es", "online", "app", "web", "www", "my", "mi", "id", "pay", "help", "service", "services",
        "clientes", "cliente", "client", "official", "oficial", "net", "direct", "go", "now", "center", "centro"
    )

    private fun isBrandVariant(token: String, brand: String): Boolean {
        if (token == brand) return true
        if (brand.length < 5) return false
        val normalized = normalizeLookalikes(token)
        if (normalized == brand) return true
        for (t in listOf(token, normalized)) {
            if (t.startsWith(brand)) {
                val rest = t.removePrefix(brand).trimEnd { it.isDigit() }
                if (rest.isEmpty() || rest in BRAND_AFFIXES || rest in PHISHING_KEYWORDS) return true
            }
            if (t.endsWith(brand)) {
                val rest = t.removeSuffix(brand)
                if (rest in BRAND_AFFIXES || rest in PHISHING_KEYWORDS) return true
            }
        }
        return false
    }

    private fun brandTokenIn(s: String): String? {
        val lower = s.lowercase()
        // Tokens split on '-' and also whole labels with '-' removed, so seg-social-gob.com still reads "segsocial".
        val candidates = tokens(lower) + lower.split('.').map { it.replace("-", "").replace("_", "") }
        for (token in candidates.distinct()) {
            for (brand in BRANDS.keys) {
                if (isBrandVariant(token, brand)) return brand
            }
        }
        return null
    }

    fun impersonatesBrand(host: String): Boolean {
        if (isOfficialDomain(host)) return false
        return brandTokenIn(host) != null
    }

    // ---------------------------------------------------------------- Data

    private val DANGEROUS_SCHEMES = setOf(
        "javascript", "vbscript", "data", "file", "content", "intent", "about", "blob",
        "android-app", "chrome", "ms-settings"
    )

    private val CRYPTO_SCHEMES = setOf(
        "bitcoin", "ethereum", "litecoin", "monero", "dogecoin", "lightning", "bitcoincash", "upi"
    )

    private val PREMIUM_PREFIXES_ES = listOf("803", "806", "807", "905", "907")

    private val SHORTENERS = setOf(
        "bit.ly", "bitly.com", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "v.gd", "buff.ly",
        "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc", "s.id", "t.ly", "rebrand.ly", "bl.ink", "qrco.de",
        "qrs.ly", "lnkd.in", "x.co", "adf.ly", "shorte.st", "urlz.fr", "tr.ee", "me-qr.com", "short.gy",
        "shorturl.com", "soo.gd", "clck.ru", "u.to", "2u.pw", "1url.com", "qr.page", "qr1.at", "l.ead.me",
        "tinu.be", "gg.gg", "surl.li", "linkr.it", "bitly.ws", "dub.sh", "beacons.page", "linktr.ee"
    )

    private val SUSPICIOUS_TLDS = setOf(
        "zip", "mov", "top", "xyz", "tk", "ml", "ga", "cf", "gq", "click", "country", "kim", "work",
        "support", "loan", "men", "date", "racing", "win", "bid", "stream", "download", "review", "party",
        "cricket", "science", "accountant", "faith", "buzz", "rest", "fit", "cam", "icu", "monster", "cyou",
        "sbs", "lol", "quest", "cfd", "bond", "best", "today", "su", "pw"
    )

    private val EXECUTABLE_EXTENSIONS = setOf(
        "apk", "xapk", "apks", "apkm", "aab", "exe", "msi", "bat", "cmd", "scr", "vbs", "vbe", "ps1", "jar",
        "dmg", "pkg", "ipa", "sh", "hta", "lnk", "pif", "com", "wsf", "mobileconfig", "deb", "appx", "msix"
    )

    private val PHISHING_KEYWORDS = setOf(
        "login", "logon", "signin", "verify", "verificar", "verificacion", "verification", "validar",
        "account", "cuenta", "cuentas", "update", "actualizar", "actualizacion", "secure", "seguridad",
        "banking", "banca", "wallet", "password", "clave", "confirm", "confirmar", "suspend", "suspendida",
        "bloqueo", "bloqueada", "desbloquear", "premio", "regalo", "gratis", "multa", "multas", "paquete",
        "envio", "entrega", "reembolso", "refund", "devolucion", "invoice", "factura", "pago", "payment",
        "support", "soporte", "recovery", "recuperar", "auth", "webscr", "token", "aviso", "alerta"
    )

    /** Brand keyword → domains that legitimately use it. */
    private val BRANDS: Map<String, List<String>> = mapOf(
        "paypal" to listOf("paypal.com", "paypal.me", "paypalobjects.com"),
        "apple" to listOf("apple.com", "icloud.com", "apple.news"),
        "icloud" to listOf("icloud.com", "apple.com"),
        "google" to listOf("google.com", "google.es", "youtube.com", "goo.gl", "g.co"),
        "gmail" to listOf("gmail.com", "google.com"),
        "microsoft" to listOf("microsoft.com", "live.com", "office.com", "microsoftonline.com", "outlook.com", "aka.ms"),
        "outlook" to listOf("outlook.com", "live.com", "microsoft.com", "office.com"),
        "office365" to listOf("office.com", "microsoft.com"),
        "amazon" to listOf("amazon.com", "amazon.es", "amazon.de", "amazon.fr", "amazon.it", "amazon.co.uk", "amzn.to"),
        "netflix" to listOf("netflix.com"),
        "spotify" to listOf("spotify.com", "spotify.link"),
        "whatsapp" to listOf("whatsapp.com", "whatsapp.net", "wa.me"),
        "facebook" to listOf("facebook.com", "fb.com", "fb.me", "meta.com"),
        "instagram" to listOf("instagram.com"),
        "tiktok" to listOf("tiktok.com"),
        "binance" to listOf("binance.com"),
        "coinbase" to listOf("coinbase.com"),
        "metamask" to listOf("metamask.io"),
        // Spanish banks, government and common delivery / utility brands used in smishing & quishing.
        "santander" to listOf("bancosantander.es", "santander.com", "santander.es", "santanderconsumer.es"),
        "bbva" to listOf("bbva.es", "bbva.com", "bbva.mx"),
        "caixabank" to listOf("caixabank.es", "caixabank.com"),
        "lacaixa" to listOf("caixabank.es", "lacaixa.es"),
        "bankinter" to listOf("bankinter.com", "bankinter.es"),
        "sabadell" to listOf("bancsabadell.com", "sabadell.com", "bancosabadell.com"),
        "unicaja" to listOf("unicajabanco.es", "unicaja.es"),
        "kutxabank" to listOf("kutxabank.es"),
        "ibercaja" to listOf("ibercaja.es"),
        "abanca" to listOf("abanca.com", "abanca.es"),
        "openbank" to listOf("openbank.es", "openbank.com"),
        "cajamar" to listOf("cajamar.es"),
        "bizum" to listOf("bizum.es"),
        "correos" to listOf("correos.es", "correos.com"),
        "dgt" to listOf("dgt.es", "dgt.gob.es"),
        "agenciatributaria" to listOf("agenciatributaria.gob.es", "agenciatributaria.es"),
        "aeat" to listOf("agenciatributaria.gob.es", "agenciatributaria.es", "aeat.es"),
        "hacienda" to listOf("hacienda.gob.es", "agenciatributaria.gob.es"),
        "segsocial" to listOf("seg-social.es", "seg-social.gob.es"),
        "sepe" to listOf("sepe.es", "sepe.gob.es"),
        "dhl" to listOf("dhl.com", "dhl.es", "dhl.de"),
        "seur" to listOf("seur.com", "seur.es"),
        "fedex" to listOf("fedex.com"),
        "iberdrola" to listOf("iberdrola.es", "iberdrola.com"),
        "endesa" to listOf("endesa.com", "endesa.es"),
        "naturgy" to listOf("naturgy.es", "naturgy.com"),
        "movistar" to listOf("movistar.es", "movistar.com", "telefonica.es", "telefonica.com"),
        "vodafone" to listOf("vodafone.es", "vodafone.com"),
        "mercadona" to listOf("mercadona.es", "mercadona.com"),
        "renfe" to listOf("renfe.com", "renfe.es"),
        "ing" to listOf("ing.es", "ing.com", "ingdirect.es"),
    )
}
