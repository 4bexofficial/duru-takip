package org.duru.takip

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

// Müdür panelden "Kameraya yönelt" dediğinde, bu servis telefonun kamerasını arka planda açıp
// saniyede birkaç JPEG kareyi sunucuya gönderir; müdür canlı görür. İstek bitince kamerayı kapatır.
// Kalıcı bir bildirimle çalışır (öğretmen uygulamayı açmadan da hazır olsun diye). Android 12+'da
// kamera açıkken sistemin yeşil kamera göstergesi + bildirim görünür; bu gizlenemez ve gizlenmez.
class KameraServisi : Service() {

	private lateinit var isThread: HandlerThread
	private lateinit var isHandler: Handler
	private val yukleyici = Executors.newSingleThreadExecutor()
	private var token: String? = null

	@Volatile private var kameraAcik = false
	@Volatile private var kapaniyor = false
	@Volatile private var yukleniyor = false
	@Volatile private var sonKareMs = 0L
	@Volatile private var sonGonderimMs = 0L

	private var cihaz: CameraDevice? = null
	private var oturum: CameraCaptureSession? = null
	private var okuyucu: ImageReader? = null
	private val cameraManager by lazy { getSystemService(CameraManager::class.java) }

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		isThread = HandlerThread("kamera-is").apply { start() }
		isHandler = Handler(isThread.looper)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		kanalKur()
		onForeground(false)
		token = Depo(this).token
		if (token == null) { stopSelf(); return START_NOT_STICKY }
		isHandler.removeCallbacksAndMessages(null)
		isHandler.post(::yoklaDongu)
		return START_STICKY
	}

	// Sunucuya "kamera isteniyor mu" diye düzenli sorar; istek durumuna göre kamerayı aç/kapat.
	private fun yoklaDongu() {
		val t = token ?: return
		val isteniyor = try { SunucuIstemci.kameraIsteniyorMu(t) } catch (e: Exception) { false }
		if (isteniyor && !kameraAcik && kameraIzniVar()) kamerayiAc()
		else if (!isteniyor && kameraAcik) kamerayiKapat()
		val sonra = if (kameraAcik) AKTIF_YOKLAMA_MS else BOSTA_YOKLAMA_MS
		isHandler.postDelayed(::yoklaDongu, sonra)
	}

	private fun kameraIzniVar() =
		ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

	private fun kamerayiAc() {
		if (kameraAcik || kapaniyor) return
		val mgr = cameraManager ?: return
		try {
			val id = arkaKameraId(mgr) ?: mgr.cameraIdList.firstOrNull() ?: return
			val ozellik = mgr.getCameraCharacteristics(id)
			val boyut = uygunBoyut(ozellik)
			okuyucu = ImageReader.newInstance(boyut.width, boyut.height, ImageFormat.YUV_420_888, 2).apply {
				setOnImageAvailableListener(::kareGeldi, isHandler)
			}
			onForeground(true) // kamera tipine yüksel (zaten çalışan FGS olduğundan izinli)
			kameraAcik = true
			sonGonderimMs = 0L
			@Suppress("MissingPermission")
			mgr.openCamera(id, object : CameraDevice.StateCallback() {
				override fun onOpened(camera: CameraDevice) {
					cihaz = camera
					oturumKur(camera)
				}
				override fun onDisconnected(camera: CameraDevice) { camera.close(); kamerayiKapat() }
				override fun onError(camera: CameraDevice, error: Int) { camera.close(); kamerayiKapat() }
			}, isHandler)
		} catch (e: Exception) {
			kamerayiKapat()
		}
	}

	private fun oturumKur(camera: CameraDevice) {
		val yuzey = okuyucu?.surface ?: return
		try {
			@Suppress("DEPRECATION")
			camera.createCaptureSession(listOf(yuzey), object : CameraCaptureSession.StateCallback() {
				override fun onConfigured(session: CameraCaptureSession) {
					oturum = session
					try {
						val istek = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
						istek.addTarget(yuzey)
						istek.set(CaptureRequest.CONTROL_MODE, CameraMetadataAuto)
						session.setRepeatingRequest(istek.build(), null, isHandler)
					} catch (e: Exception) { kamerayiKapat() }
				}
				override fun onConfigureFailed(session: CameraCaptureSession) { kamerayiKapat() }
			}, isHandler)
		} catch (e: Exception) { kamerayiKapat() }
	}

	// Her YUV karesi: en yenisini al, kapat. Hız sınırına uyuyorsa JPEG'e çevirip yüklemeye bırak.
	private fun kareGeldi(reader: ImageReader) {
		val img: Image? = try { reader.acquireLatestImage() } catch (e: Exception) { null }
		if (img == null) return
		try {
			val simdi = System.currentTimeMillis()
			if (!yukleniyor && simdi - sonGonderimMs >= KARE_ARALIK_MS && kameraAcik) {
				val jpeg = yuvToJpeg(img)
				sonGonderimMs = simdi
				val t = token
				if (jpeg != null && t != null) {
					yukleniyor = true
					yukleyici.execute {
						val devam = try { SunucuIstemci.kameraKareGonder(t, jpeg) } catch (e: Exception) { false }
						yukleniyor = false
						sonKareMs = System.currentTimeMillis()
						if (!devam) isHandler.post(::kamerayiKapat) // müdür durdurduysa hemen kapat
					}
				}
			}
		} catch (e: Exception) {
			// bir kareyi atlamak sorun değil
		} finally {
			img.close()
		}
	}

	private fun kamerayiKapat() {
		if (kapaniyor) return
		kapaniyor = true
		try { oturum?.close() } catch (e: Exception) {}
		try { cihaz?.close() } catch (e: Exception) {}
		try { okuyucu?.close() } catch (e: Exception) {}
		oturum = null; cihaz = null; okuyucu = null
		kameraAcik = false
		kapaniyor = false
		onForeground(false)
	}

	private fun arkaKameraId(mgr: CameraManager): String? {
		for (id in mgr.cameraIdList) {
			val yon = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
			if (yon == CameraCharacteristics.LENS_FACING_BACK) return id
		}
		return null
	}

	private fun uygunBoyut(ozellik: CameraCharacteristics): Size {
		val harita = ozellik.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
		val boyutlar = harita?.getOutputSizes(ImageFormat.YUV_420_888)
		if (boyutlar.isNullOrEmpty()) return Size(640, 480)
		val hedef = 640 * 480
		// Alanı 640x480'e en yakın, aşırı büyük olmayan (bant genişliği için) boyutu seç.
		return boyutlar.filter { it.width <= 1280 && it.height <= 1280 }.minByOrNull { kotlin.math.abs(it.width * it.height - hedef) }
			?: boyutlar.minByOrNull { kotlin.math.abs(it.width * it.height - hedef) }
			?: Size(640, 480)
	}

	private fun yuvToJpeg(image: Image): ByteArray? {
		return try {
			val nv21 = yuv420ToNv21(image)
			val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
			val cikti = ByteArrayOutputStream()
			yuv.compressToJpeg(Rect(0, 0, image.width, image.height), JPEG_KALITE, cikti)
			cikti.toByteArray()
		} catch (e: Exception) { null }
	}

	// YUV_420_888 → NV21 (satır/piksel adımlarını dikkate alarak).
	private fun yuv420ToNv21(image: Image): ByteArray {
		val w = image.width; val h = image.height
		val ySize = w * h
		val nv21 = ByteArray(ySize + ySize / 2)
		val y = image.planes[0]; val u = image.planes[1]; val v = image.planes[2]
		val yBuf = y.buffer; val yRow = y.rowStride
		if (yRow == w) { yBuf.get(nv21, 0, ySize) }
		else { for (satir in 0 until h) { yBuf.position(satir * yRow); yBuf.get(nv21, satir * w, w) } }
		val uBuf = u.buffer; val vBuf = v.buffer
		val uRow = u.rowStride; val vRow = v.rowStride
		val uPix = u.pixelStride; val vPix = v.pixelStride
		val cw = w / 2; val chh = h / 2
		var idx = ySize
		for (satir in 0 until chh) {
			var uPos = satir * uRow; var vPos = satir * vRow
			for (sutun in 0 until cw) {
				nv21[idx++] = vBuf.get(vPos)
				nv21[idx++] = uBuf.get(uPos)
				uPos += uPix; vPos += vPix
			}
		}
		return nv21
	}

	private fun onForeground(kamera: Boolean) {
		// Kamera AKTİFKEN açıkça belirtilir (örtülü gözetim yapılmaz): kayıt/izleme sırasında
		// kullanıcı bunu bildirimden ve Android 12+ yeşil nokta göstergesinden görebilir.
		val metin = if (kamera) "Yönetici kamerayı görüntülüyor" else "Duru Takip çalışıyor"
		val bildirim = bildirim(metin)
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				val tur = if (kamera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
				startForeground(BILDIRIM_ID, bildirim, tur)
			} else {
				startForeground(BILDIRIM_ID, bildirim)
			}
		} catch (e: Exception) {
			// kamera tipine yükseltilemezse (nadiren) bu turda kamerayı kullanma
			if (kamera) { kameraAcik = false }
		}
	}

	private fun kanalKur() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val k = NotificationChannel(KANAL, "Çalışma durumu", NotificationManager.IMPORTANCE_MIN)
			getSystemService(NotificationManager::class.java).createNotificationChannel(k)
		}
	}

	private fun bildirim(metin: String): Notification =
		NotificationCompat.Builder(this, KANAL)
			.setContentTitle("Duru Takip")
			.setContentText(metin)
			.setSmallIcon(R.drawable.ic_launcher_monochrome)
			.setOngoing(true)
			.build()

	override fun onDestroy() {
		isHandler.post { kamerayiKapat() }
		isHandler.removeCallbacksAndMessages(null)
		isThread.quitSafely()
		yukleyici.shutdown()
		super.onDestroy()
	}

	companion object {
		const val KANAL = "duru_kamera"
		const val BILDIRIM_ID = 7
		const val BOSTA_YOKLAMA_MS = 20_000L  // kamera kapalıyken sunucuyu bu sıklıkta sorar (mobil veri/pil için seyrek)
		const val AKTIF_YOKLAMA_MS = 4_000L   // kamera açıkken istek hâlâ var mı diye bakma sıklığı
		const val KARE_ARALIK_MS = 350L       // gönderilen kareler arası en az süre (~3 fps)
		const val JPEG_KALITE = 60
		const val CameraMetadataAuto = android.hardware.camera2.CameraMetadata.CONTROL_MODE_AUTO

		fun baslat(ctx: Context) {
			if (Depo(ctx).token == null) return
			try { ContextCompat.startForegroundService(ctx, Intent(ctx, KameraServisi::class.java)) } catch (e: Exception) {}
		}
	}
}
