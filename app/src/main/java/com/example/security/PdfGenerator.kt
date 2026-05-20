package com.example.security

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.GovTaskEntity
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {
    fun generateFormPdf(context: Context, task: GovTaskEntity, decryptedFields: Map<String, String>): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = android.graphics.Color.BLACK
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = 11f
            color = android.graphics.Color.DKGRAY
        }
        val textPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = android.graphics.Color.BLACK
        }
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = android.graphics.Color.LTGRAY
        }

        var y = 40f

        // 1. Draw Title Header Card
        canvas.drawRect(20f, y, 575f, y + 50f, Paint().apply { color = android.graphics.Color.argb(30, 79, 70, 229) })
        canvas.drawRect(20f, y, 575f, y + 50f, borderPaint)
        canvas.drawText("GOVTASKA OFFICIAL COMPLIANCE PACKET", 40f, y + 32f, titlePaint)
        y += 75f

        // 2. Draw Metadata Cards
        canvas.drawText("FILE ID: G-${task.id % 10000}", 30f, y, headerPaint)
        canvas.drawText("DATE EXPORTED: 2026-05-20", 350f, y, textPaint)
        y += 20f
        canvas.drawText("APPLICANT NAME: ${task.applicantName.uppercase()}", 30f, y, headerPaint)
        y += 20f
        canvas.drawText("OFFICIAL MODULE / DESK: ${task.module.uppercase()}", 30f, y, textPaint)
        canvas.drawText("STATUS Clearance: ${task.status}", 350f, y, headerPaint)
        y += 30f

        canvas.drawLine(20f, y, 575f, y, borderPaint)
        y += 25f

        // 3. Draw Plain-text Field Form Matrix
        canvas.drawText("I. ENCRYPTED FIELD REGISTRY DECRYPTED IN SECURE MEMORY", 30f, y, headerPaint)
        y += 25f

        decryptedFields.forEach { (key, value) ->
            if (y > 720f) return@forEach // Basic overflow safety
            canvas.drawRect(30f, y - 12f, 565f, y + 10f, Paint().apply { color = android.graphics.Color.argb(8, 0, 0, 0) })
            canvas.drawRect(30f, y - 12f, 565f, y + 10f, borderPaint)
            canvas.drawText(key.replace("_", " ").uppercase() + ":", 40f, y, headerPaint)
            canvas.drawText(value, 260f, y, textPaint)
            y += 30f
        }

        y += 10f
        if (y < 750f) {
            canvas.drawLine(20f, y, 575f, y, borderPaint)
            y += 25f
            canvas.drawText("II. UNDERWRITING BOT EVALUATION & ACTION REASONING", 30f, y, headerPaint)
            y += 20f
            
            // Draw evaluation text word-wrapped nicely
            val words = task.aiEvaluationReason.split(" ")
            val lineBuilder = StringBuilder()
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            
            for (word in words) {
                if (paint.measureText(lineBuilder.toString() + word) > 520f) {
                    canvas.drawText(lineBuilder.toString(), 35f, y, paint)
                    y += 15f
                    lineBuilder.clear()
                    if (y > 800f) break
                }
                lineBuilder.append(word).append(" ")
            }
            if (lineBuilder.isNotEmpty() && y <= 800f) {
                canvas.drawText(lineBuilder.toString(), 35f, y, paint)
                y += 15f
            }
        }

        pdfDocument.finishPage(page)

        return try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "GovTask_Packet_G-${task.id % 10000}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            pdfDocument.close()
            e.printStackTrace()
            null
        }
    }
}
