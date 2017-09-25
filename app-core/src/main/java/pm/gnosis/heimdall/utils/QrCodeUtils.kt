package pm.gnosis.heimdall.utils

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.support.v4.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import pm.gnosis.heimdall.utils.zxing.ZxingIntentIntegrator

fun generateQrCode(content: String, width: Int = 512, height: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    try {
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0..width - 1) {
            for (y in 0..height - 1) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    } catch (e: WriterException) {
        throw e
    }
}

fun Activity.scanQrCode() = ZxingIntentIntegrator(this).initiateScan(ZxingIntentIntegrator.QR_CODE_TYPES)
fun Fragment.scanQrCode() = ZxingIntentIntegrator(this).initiateScan(ZxingIntentIntegrator.QR_CODE_TYPES)