package net.hilson.qrieux.security

import net.hilson.qrieux.util.QrContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrRiskAnalyzerTest {

    private fun analyze(raw: String) = QrRiskAnalyzer.analyze(QrContentType.fromRawValue(raw))
    private fun reasons(raw: String) = analyze(raw).reasons

    // ---- Safe, legitimate codes must not cry wolf

    @Test fun plainHttpsSiteIsSafe() = assertEquals(RiskLevel.SAFE, analyze("https://www.wikipedia.org/wiki/QR_code").level)
    @Test fun officialBankIsSafe() = assertEquals(RiskLevel.SAFE, analyze("https://www.bbva.es/personas.html").level)
    @Test fun officialGovIsSafe() = assertEquals(RiskLevel.SAFE, analyze("https://sede.agenciatributaria.gob.es/Sede/inicio.html").level)
    @Test fun officialLoginIsSafe() = assertEquals(RiskLevel.SAFE, analyze("https://login.microsoftonline.com/").level)
    @Test fun pineappleIsNotApple() = assertFalse(RiskReason.BRAND_IMPERSONATION in reasons("https://www.pineapple-recipes.com"))
    @Test fun shoppingIsNotIng() = assertFalse(RiskReason.BRAND_IMPERSONATION in reasons("https://shopping.example.com"))
    @Test fun wpaWifiIsSafe() = assertEquals(RiskLevel.SAFE, analyze("WIFI:T:WPA;S:Casa;P:supersecret;;").level)
    @Test fun normalPhoneIsSafe() = assertEquals(RiskLevel.SAFE, analyze("tel:+34600123456").level)
    @Test fun plainTextIsSafe() = assertEquals(RiskLevel.SAFE, analyze("Hola, esto es un texto").level)

    // ---- Link tricks

    @Test fun userInfoTrick() {
        val r = analyze("https://www.paypal.com@evil.example.top/login")
        assertEquals(RiskLevel.DANGER, r.level)
        assertTrue(RiskReason.USERINFO_TRICK in r.reasons)
        assertTrue(RiskReason.BRAND_IMPERSONATION in r.reasons)
        assertEquals("example.top", r.realDomain)
    }

    @Test fun brandInSubdomain() {
        val r = analyze("https://paypal.com.secure-login.xyz/")
        assertEquals(RiskLevel.DANGER, r.level)
        assertTrue(RiskReason.BRAND_IMPERSONATION in r.reasons)
        assertTrue(RiskReason.SUSPICIOUS_TLD in r.reasons)
        assertEquals("secure-login.xyz", r.realDomain)
    }

    @Test fun lookalikeDigits() = assertTrue(RiskReason.BRAND_IMPERSONATION in reasons("https://paypa1.com/verify"))
    @Test fun hyphenatedBrand() = assertTrue(RiskReason.BRAND_IMPERSONATION in reasons("https://correos-entrega.com/paquete"))
    @Test fun spanishBankPhish() = assertTrue(RiskReason.BRAND_IMPERSONATION in reasons("https://bbva-clientes.net/acceso"))
    @Test fun dgtFine() = assertTrue(RiskReason.BRAND_IMPERSONATION in reasons("https://dgt-multas.info/pagar"))
    @Test fun punycode() = assertTrue(RiskReason.PUNYCODE_HOST in reasons("https://xn--pypal-4ve.com/"))
    @Test fun nonAscii() = assertTrue(RiskReason.NON_ASCII_HOST in reasons("https://аpple.com/")) // Cyrillic а
    @Test fun ipHost() = assertTrue(RiskReason.IP_ADDRESS_HOST in reasons("http://192.168.1.20:8080/admin"))
    @Test fun decimalIpHost() = assertTrue(RiskReason.IP_ADDRESS_HOST in reasons("http://3232235777/"))
    @Test fun unusualPort() = assertTrue(RiskReason.UNUSUAL_PORT in reasons("https://example.com:8443/"))
    @Test fun insecureHttp() = assertEquals(RiskLevel.CAUTION, analyze("http://example.com/").level)
    @Test fun shortener() = assertTrue(RiskReason.URL_SHORTENER in reasons("https://bit.ly/3abcDEF"))
    @Test fun apkDownload() = assertEquals(RiskLevel.DANGER, analyze("https://example.com/files/update.apk").level)
    @Test fun embeddedRedirect() = assertTrue(RiskReason.EMBEDDED_REDIRECT in reasons("https://example.com/r?url=https%3A%2F%2Fevil.com"))
    @Test fun phishingKeywordInHost() = assertTrue(RiskReason.PHISHING_KEYWORDS in reasons("https://verify-account-now.com/"))
    @Test fun hiddenBidi() = assertTrue(RiskReason.HIDDEN_CHARACTERS in reasons("https://example.com/‮gnp.exe"))

    // ---- Other schemes

    @Test fun javascriptBlocked() {
        val c = QrContentType.fromRawValue("javascript:alert(document.cookie)")
        assertTrue(c is QrContentType.OtherUri)
        assertEquals(RiskLevel.DANGER, QrRiskAnalyzer.analyze(c).level)
        assertTrue(QrRiskAnalyzer.isBlockedScheme("javascript"))
    }

    @Test fun intentBlocked() = assertTrue(RiskReason.DANGEROUS_SCHEME in reasons("intent://scan/#Intent;scheme=zxing;package=com.evil;end"))
    @Test fun dataUriBlocked() = assertTrue(RiskReason.DANGEROUS_SCHEME in reasons("data:text/html;base64,PHNjcmlwdD4="))
    @Test fun bitcoinPayment() = assertTrue(RiskReason.CRYPTO_PAYMENT in reasons("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.5"))
    @Test fun otpSecret() = assertTrue(RiskReason.OTP_SECRET in reasons("otpauth://totp/Example:me@example.com?secret=JBSWY3DPEHPK3PXP"))
    @Test fun marketLink() = assertEquals(RiskLevel.CAUTION, analyze("market://details?id=com.example").level)

    // ---- Phone / SMS

    @Test fun mmiCallForward() {
        val r = analyze("tel:*21*666555444%23")
        assertEquals(RiskLevel.DANGER, r.level)
        assertTrue(QrRiskAnalyzer.isMmiCode("*21*666555444#"))
    }

    @Test fun premiumSpain() = assertTrue(RiskReason.PREMIUM_NUMBER in reasons("tel:+34806123456"))
    @Test fun premiumSpainLocal() = assertTrue(QrRiskAnalyzer.isPremiumNumber("905 12 34 56"))
    @Test fun regularMobileNotPremium() = assertFalse(QrRiskAnalyzer.isPremiumNumber("+34 600 12 34 56"))
    @Test fun smsShortNumber() {
        val r = analyze("SMSTO:25521:ALTA JUEGOS")
        assertTrue(RiskReason.SMS_SHORT_NUMBER in r.reasons)
        assertTrue(RiskReason.SMS_PREFILLED_BODY in r.reasons)
    }

    // ---- Wi-Fi

    @Test fun openWifi() = assertTrue(RiskReason.OPEN_WIFI in reasons("WIFI:T:nopass;S:Free_Airport;;"))
    @Test fun wepWifi() = assertTrue(RiskReason.WEAK_WIFI in reasons("WIFI:T:WEP;S:Old;P:12345;;"))

    // ---- Email / text

    @Test fun phishingEmailDomain() = assertTrue(RiskReason.BRAND_IMPERSONATION in reasons("mailto:soporte@santander-seguridad.com"))
    @Test fun textWithBadLink() {
        val r = analyze("Su paquete está retenido. Pague aquí: https://correos-aduanas.top/pago")
        assertTrue(RiskReason.TEXT_CONTAINS_LINK in r.reasons)
        assertEquals(RiskLevel.DANGER, r.level)
    }

    // ---- v1.1: gaps found in the audit

    @Test fun schemelessWwwIsAnalysed() {
        val c = QrContentType.fromRawValue("www.paypal-verify.com")
        assertTrue(c is QrContentType.Url)
        assertEquals("https://www.paypal-verify.com", (c as QrContentType.Url).url)
        assertEquals(RiskLevel.DANGER, QrRiskAnalyzer.analyze(c).level)
    }

    @Test fun schemelessWithPathIsUrl() = assertTrue(QrContentType.fromRawValue("example.com/menu") is QrContentType.Url)
    @Test fun plainWordWithDotStaysText() = assertTrue(QrContentType.fromRawValue("informe.pdf") is QrContentType.Text)

    @Test fun googleRedirectShowsFinalDestination() {
        val r = analyze("https://www.google.com/url?q=https://evil.top/login&sa=D")
        assertEquals("evil.top", r.realDomain)
        assertTrue(RiskReason.EMBEDDED_REDIRECT in r.reasons)
        assertTrue(RiskReason.SUSPICIOUS_TLD in r.reasons)
    }

    @Test fun facebookRedirectToBrandPhish() {
        val r = analyze("https://l.facebook.com/l.php?u=https%3A%2F%2Fcorreos-aduanas.top%2Fpago")
        assertEquals(RiskLevel.DANGER, r.level)
        assertEquals("correos-aduanas.top", r.realDomain)
    }

    @Test fun sharedHostingIsTheSubdomain() {
        val r = analyze("https://caixabank-es.web.app/acceso")
        assertEquals("caixabank-es.web.app", r.realDomain)
        assertTrue(RiskReason.BRAND_IMPERSONATION in r.reasons)
        assertTrue(RiskReason.USER_CONTENT_HOST in r.reasons)
    }

    @Test fun googleFormsIsUserContent() = assertTrue(RiskReason.USER_CONTENT_HOST in reasons("https://docs.google.com/forms/d/e/1FAIpQLSx/viewform"))
    @Test fun ipfsIsUserContent() = assertTrue(RiskReason.USER_CONTENT_HOST in reasons("https://ipfs.io/ipfs/QmXyz/login.html"))
    @Test fun googleDocsHomeIsSafe() = assertEquals(RiskLevel.SAFE, analyze("https://www.google.com/").level)

    @Test fun brandDomainInPath() {
        val r = analyze("https://evil.com/paypal.com/login")
        assertEquals(RiskLevel.DANGER, r.level)
        assertTrue(RiskReason.BRAND_IN_PATH in r.reasons)
    }

    @Test fun brandWordWithScamWordInPath() = assertTrue(RiskReason.BRAND_IN_PATH in reasons("https://tienda-ejemplo.com/santander/verificar"))
    @Test fun newsAboutBrandIsFine() = assertFalse(RiskReason.BRAND_IN_PATH in reasons("https://www.elpais.com/economia/2026/bbva-resultados.html"))
    @Test fun officialSiteMentioningItselfIsFine() = assertEquals(RiskLevel.SAFE, analyze("https://www.paypal.com/es/paypal.com/login").level)

    @Test fun secureSubdomainOfRealSiteIsSafe() = assertEquals(RiskLevel.SAFE, analyze("https://secure.booking.com/myreservations.html").level)
    @Test fun scamWordsInRegisteredDomainStillFlagged() = assertTrue(RiskReason.PHISHING_KEYWORDS in reasons("https://login-verify.example-bank.com"))

    @Test fun registrableDomain() {
        assertEquals("example.co.uk", QrRiskAnalyzer.registrableDomain("a.b.example.co.uk"))
        assertEquals("agenciatributaria.gob.es", QrRiskAnalyzer.registrableDomain("sede.agenciatributaria.gob.es"))
        assertEquals("evil.com", QrRiskAnalyzer.registrableDomain("paypal.com.evil.com"))
    }
}
