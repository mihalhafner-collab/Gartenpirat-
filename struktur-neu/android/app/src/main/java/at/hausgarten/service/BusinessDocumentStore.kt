package at.hausgarten.service

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import org.json.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

class BusinessDocumentStore {
private val directory: File
private val staging: File
constructor(c: Context) {
directory = File(c.getFilesDir(), "business-documents")
staging = File(c.cacheDir, "immutable-document-staging")
}
@Throws(Exception::class) fun create(offer: JSONObject): JSONObject {

var bytes = ByteArrayOutputStream()
var pdf = PdfDocument()
var w: Writer? = null
try {

w = Writer(pdf, offer.optString("offer_number"))
var issuer = offer.getJSONObject("issuer")
var recipient = offer.getJSONObject("recipient")
w.line(OfferRules.label(offer.getString("document_type")))
w.line(offer.getString("offer_number"))
w.line("Datum: " + offer.getString("issue_date") + " | Gültig bis: " + offer.getString("valid_until"))
for (p in arrayOf(issuer, recipient)) {
w.line("")
for (k in (if (p == issuer) arrayOf("business_name", "owner_name", "business_street") else arrayOf("name", "company", "street"))) {
if (!(p.optString(k).isEmpty())) {
w.line(p.getString(k))
}
}
w.line(p.optString((if (p == issuer) "business_postal_code" else "postal_code")) + " " + p.optString((if (p == issuer) "business_city" else "city")))
}
w.line("")
w.line(offer.getString("title"))
w.line(offer.optString("header_text"))
w.line("Ausführung: " + offer.getString("execution_window"))
if (!(offer.optString("location").isEmpty())) {
w.line("Einsatzort: " + offer.getString("location"))
}
var lines = offer.getJSONArray("positions")
run {
var i: Int = 0
var firstIteration33 = true
while (true) {
if (firstIteration33) firstIteration33 = false else { i++ }
if (!(i < lines.length())) break

var p = lines.getJSONObject(i)
w.line("")
w.line(java.lang.String.valueOf((i + 1)) + ". " + p.optString("title"))
w.line(p.optString("description"))
w.line(java.lang.String.valueOf(p.opt("quantity")) + " " + p.optString("unit") + " × " + money(p.get("price")) + " netto = " + money(p.get("net")) + " netto | USt " + p.optDouble("tax_rate", 0.0) + " %")

}
}
w.line("")
w.line("Netto: " + money(offer.get("net_total")))
w.line("Umsatzsteuer: " + money(offer.get("tax_total")))
w.line("Gesamt inklusive Umsatzsteuer: " + money(offer.get("gross_total")))
if ((offer.optString("tax_mode") == "small_business")) {
w.line(issuer.optString("tax_exemption_text"))
} else {
w.line("UID: " + issuer.optString("business_vat_id"))
}
if (!(offer.optString("binding_notice").isEmpty())) {
w.line(offer.getString("binding_notice"))
}
if (offer.optBoolean("test_record")) {
w.line("TESTDOKUMENT – NICHT VERSENDEN – KEIN PRODUKTIVER BELEG")
}
for (pair in arrayOf(arrayOf("payment_terms", "Zahlungsbedingungen"), arrayOf("assumptions", "Annahmen"), arrayOf("exclusions", "Nicht enthalten"), arrayOf("notes", "Hinweise"), arrayOf("consumer_information", "Verbraucherinformationen"), arrayOf("footer_text", ""))) {
if (!(offer.optString(pair[0]).isEmpty())) {
w.line("")
w.line(pair[1])
w.line(offer.getString(pair[0]))
}
}
w.finish()
pdf.writeTo(bytes)

} finally {
if (w != null) {
w.finish()
}
pdf.close()
}
var raw = bytes.toByteArray()
if (raw.size < 5 || raw.size > 20 * 1024 * 1024) {
throw IOException("Angebots-PDF überschreitet die sichere Dateigrenze.")
}
var hash = PhotoAttachmentStore.sha256(raw)
var meta = JSONObject().put("document_id", "offer:" + offer.getString("id")).put("pdf_filename", offer.getString("offer_number") + ".pdf").put("pdf_storage_name", hash + ".pdf").put("pdf_sha256", hash).put("pdf_byte_size", raw.size)
store(raw, meta)
return meta
}
@Throws(Exception::class) fun store(raw: ByteArray, meta: JSONObject): Unit {

verify(raw, meta)
synchronized(FILE_LOCK) {
if (!(directory.isDirectory()) && !(directory.mkdirs())) {
throw IOException("Dokumentspeicher fehlt.")
}
var f = file(meta)
if (f.exists()) {
read(meta)
return
}
if (!staging.isDirectory && !staging.mkdirs()) throw IOException("Dokument-Zwischenspeicher fehlt.")
val temporary = File.createTempFile("document-", ".tmp", staging)
try {
FileOutputStream(temporary).use { output -> output.write(raw); output.fd.sync() }
if (temporary.length() != raw.size.toLong() || fileHash(temporary) != meta.getString("pdf_sha256")) throw IOException("Dokument-Prüfsumme stimmt nicht überein.")
// The published content-addressed file is never opened for writing again.
Files.move(temporary.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE)
} finally {
temporary.delete()
}
}
}
@Throws(Exception::class) fun read(meta: JSONObject): ByteArray {

var f = file(meta)
if (f.length() > 20 * 1024 * 1024) {
throw IOException("Dokument ist zu groß.")
}
var raw = java.nio.file.Files.readAllBytes(f.toPath())
verify(raw, meta)
return raw
}
@Throws(Exception::class) fun deleteVerified(meta: JSONObject): Unit {

var f = file(meta)
if (!(f.exists())) {
return
}
read(meta)
if (!(f.delete())) {
throw IOException("Dokument konnte nicht entfernt werden.")
}
}
private @Throws(Exception::class) fun file(meta: JSONObject): File {

var name = meta.getString("pdf_storage_name")
if (!(name.matches(Regex("[0-9a-f]{64}\\.pdf")))) {
throw IllegalArgumentException("Ungültige Dokumentreferenz.")
}
return File(directory, name)
}
private class Writer {
@JvmField val pdf: PdfDocument
@JvmField val number: String
@JvmField val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
@JvmField var page: PdfDocument.Page? = null
@JvmField var y: Float = 0f
@JvmField var count: Int = 0
constructor(pdf: PdfDocument, number: String) {
this.pdf = pdf
this.number = number
paint.setTextSize(11f)
newPage()
}
fun newPage(): Unit {

if (page != null) {
pdf.finishPage(page)
}
if (++count > 100) {
throw IllegalArgumentException("Angebot überschreitet 100 Seiten.")
}
page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, count).create())
y = 50f
page!!.getCanvas().drawText(number + " · " + count, 45f, y, paint)
y += 30
}
fun line(text: String): Unit {

for (paragraph in java.lang.String.valueOf(text).split(Regex("\n"))) {
var rest = paragraph
do {
if (y > 790) {
newPage()
}
var n: Int = (if (rest.isEmpty()) 0 else paint.breakText(rest, true, 505f, null))
if (n == 0 && !(rest.isEmpty())) {
n = 1
}
if (n < rest.length) {
var space: Int = rest.lastIndexOf(' ', n)
if (space > 0) {
n = space
}
}
page!!.getCanvas().drawText(rest.substring(0, n), 45f, y, paint)
y += 16
rest = rest.substring(n).replaceFirst(Regex("^ +"), "")
} while (!(rest.isEmpty()))
}
}
fun finish(): Unit {

if (page != null) {
pdf.finishPage(page)
page = null
}
}
}
companion object {
private val FILE_LOCK = Any()
private fun fileHash(file: File): String {
val digest = MessageDigest.getInstance("SHA-256")
FileInputStream(file).use { input ->
val block = ByteArray(8192)
while (true) { val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) }
}
return digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
}
private @JvmStatic @Throws(Exception::class) fun verify(raw: ByteArray, meta: JSONObject): Unit {

if (raw.size < 5 || raw.size > 20 * 1024 * 1024 || !((meta.getString("pdf_storage_name") == meta.getString("pdf_sha256") + ".pdf")) || !((String(raw, 0, 5, java.nio.charset.StandardCharsets.US_ASCII) == "%PDF-")) || raw.size.toLong() != meta.getLong("pdf_byte_size") || !((PhotoAttachmentStore.sha256(raw) == meta.getString("pdf_sha256")))) {
throw IOException("Dokument-Prüfsumme stimmt nicht überein.")
}
}
private @JvmStatic fun money(n: Any): String {

return java.math.BigDecimal(n.toString()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " EUR"
}
}
}
