package at.hausgarten.service

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Bounded EXIF parsing; no location metadata is copied to a new JPEG. */
class PhotoPolicy {
    companion object {
        @JvmStatic fun edge(quality: String?, profile: Boolean): Int = if (profile) 1024 else if (quality == "high") 4096 else 3072

        @JvmStatic fun orientation(b: ByteArray): Int {
            try {
                if (b.size >= 8 && b[0] == 137.toByte() && b[1] == 'P'.code.toByte()) {
                    var p = 8
                    while (p + 12 <= b.size) {
                        val n = u32(b, p, false)
                        if (n > b.size - p - 12) break
                        if (b[p + 4] == 'e'.code.toByte() && b[p + 5] == 'X'.code.toByte() && b[p + 6] == 'I'.code.toByte() && b[p + 7] == 'f'.code.toByte()) return tiff(b, p + 8, p + 8 + n.toInt())
                        p += 12 + n.toInt()
                    }
                }
                if (b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte()) {
                    var p = 12
                    while (p + 8 <= b.size) {
                        val n = u32(b, p + 4, true)
                        if (n > b.size - p - 8) break
                        if (b[p] == 'E'.code.toByte() && b[p + 1] == 'X'.code.toByte() && b[p + 2] == 'I'.code.toByte() && b[p + 3] == 'F'.code.toByte()) {
                            var t = p + 8
                            if (n >= 6 && b[t] == 'E'.code.toByte() && b[t + 1] == 'x'.code.toByte()) t += 6
                            return tiff(b, t, p + 8 + n.toInt())
                        }
                        p += 8 + n.toInt() + (n.toInt() and 1)
                    }
                }
                if (b.size < 4 || b[0].toInt() and 255 != 255 || b[1].toInt() and 255 != 216) return 1
                var p = 2
                while (p + 4 < b.size) {
                    if (b[p].toInt() and 255 != 255) break
                    val marker = b[p + 1].toInt() and 255
                    if (marker == 218 || marker == 217) break
                    val n = ((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)
                    if (n < 2 || p + 2 + n > b.size) break
                    if (marker == 225 && n >= 16 && b[p + 4] == 'E'.code.toByte() && b[p + 5] == 'x'.code.toByte() && b[p + 6] == 'i'.code.toByte() && b[p + 7] == 'f'.code.toByte()) return tiff(b, p + 10, p + 2 + n)
                    p += 2 + n
                }
            } catch (_: IndexOutOfBoundsException) { return 1 }
            return 1
        }

        private fun tiff(b: ByteArray, t: Int, end: Int): Int {
            if (t + 8 > end) return 1
            val little = b[t] == 'I'.code.toByte() && b[t + 1] == 'I'.code.toByte()
            if (!little && !(b[t] == 'M'.code.toByte() && b[t + 1] == 'M'.code.toByte())) return 1
            val offset = u32(b, t + 4, little)
            if (offset < 8 || offset > end - t - 2) return 1
            val d = t + offset.toInt()
            val count = u16(b, d, little)
            for (j in 0 until minOf(count, 1024)) {
                val q = d + 2 + j * 12
                if (q + 12 > end) break
                if (u16(b, q, little) == 274 && u16(b, q + 2, little) == 3 && u32(b, q + 4, little) == 1L) {
                    val v = u16(b, q + 8, little)
                    return if (v in 1..8) v else 1
                }
            }
            return 1
        }

        @JvmStatic fun withoutMetadata(b: ByteArray, mime: String): ByteArray {
            if (mime == "image/jpeg") return withoutJpegMetadata(b)
            val out = ByteArrayOutputStream(b.size)
            if (mime == "image/png" && b.size >= 8) {
                out.write(b, 0, 8)
                var p = 8
                while (p + 12 <= b.size) {
                    val n = u32(b, p, false)
                    if (n > b.size - p - 12) return b
                    val type = String(b, p + 4, 4, StandardCharsets.US_ASCII)
                    if (type !in listOf("eXIf", "tEXt", "iTXt", "zTXt")) out.write(b, p, 12 + n.toInt())
                    p += 12 + n.toInt()
                }
                if (p != b.size) return b
                return out.toByteArray()
            }
            if (mime == "image/webp" && b.size >= 12) {
                out.write(b, 0, 12)
                var p = 12
                while (p + 8 <= b.size) {
                    val n = u32(b, p + 4, true)
                    if (n > b.size - p - 8) return b
                    val type = String(b, p, 4, StandardCharsets.US_ASCII)
                    val size = 8 + n.toInt() + (n.toInt() and 1)
                    if (p + size > b.size) return b
                    if (type != "EXIF" && type != "XMP ") {
                        val chunk = b.copyOfRange(p, p + size)
                        if (type == "VP8X" && n >= 1) chunk[8] = (chunk[8].toInt() and 12.inv()).toByte()
                        out.write(chunk, 0, chunk.size)
                    }
                    p += size
                }
                if (p != b.size) return b
                val result = out.toByteArray()
                val size = result.size - 8
                for (i in 0 until 4) result[4 + i] = (size ushr (8 * i)).toByte()
                return result
            }
            return b
        }

        private fun u16(b: ByteArray, p: Int, little: Boolean): Int = if (little) (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8) else ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
        private fun u32(b: ByteArray, p: Int, little: Boolean): Long = if (little) (u16(b, p, little).toLong() and 65535L) or (u16(b, p + 2, little).toLong() shl 16) else (u16(b, p, little).toLong() shl 16) or (u16(b, p + 2, little).toLong() and 65535L)

        @JvmStatic fun withoutJpegMetadata(b: ByteArray): ByteArray {
            if (b.size < 4 || b[0].toInt() and 255 != 255 || b[1].toInt() and 255 != 216) return b
            val out = ByteArrayOutputStream(b.size)
            out.write(b, 0, 2)
            var p = 2
            while (p + 4 <= b.size && b[p].toInt() and 255 == 255) {
                val marker = b[p + 1].toInt() and 255
                if (marker == 218 || marker == 217) break
                val n = ((b[p + 2].toInt() and 255) shl 8) or (b[p + 3].toInt() and 255)
                if (n < 2 || p + 2 + n > b.size) return b
                // Strip EXIF/XMP/IPTC/comments; retain color profiles and coding tables.
                if (marker != 225 && marker != 237 && marker != 254) out.write(b, p, n + 2)
                p += n + 2
            }
            out.write(b, p, b.size - p)
            return out.toByteArray()
        }
    }
}
