package com.example.mapszindex

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Minimal repro: Marker zIndex is not respected between flat and non-flat (billboard) markers.
 *
 * Four rows, each with two overlapping markers at the same LatLng. In every row the RED marker
 * has zIndex 2 and the BLUE marker zIndex 1, so RED must always be drawn on top of BLUE.
 * The RED marker is added first in every row, so insertion order would put BLUE on top —
 * only zIndex can make RED win.
 *
 *   Row A: RED flat,      BLUE billboard  <- mixed: RED expected on top
 *   Row B: RED flat,      BLUE flat       <- control
 *   Row C: RED billboard, BLUE billboard  <- control
 *   Row D: RED billboard, BLUE flat       <- mixed: RED expected on top
 */
class MainActivity : Activity() {

    private lateinit var mapView: MapView
    private lateinit var infoView: TextView
    private var rendererName = "pending"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        infoView = TextView(this).apply {
            setBackgroundColor(Color.argb(220, 255, 255, 255))
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val pad = dp(8)
            setPadding(pad, pad + dp(32), pad, pad)
        }

        // The callback may run synchronously, so infoView must exist before this call.
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
            rendererName = renderer.name
            Log.i(TAG, "Maps renderer: $renderer")
            updateInfo()
        }

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        setContentView(
            FrameLayout(this).apply {
                addView(mapView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(infoView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
            }
        )
        updateInfo()

        mapView.getMapAsync(::onMapReady)
    }

    private fun onMapReady(map: GoogleMap) {
        val rows = listOf(
            Row("A", redFlat = true, blueFlat = false),
            Row("B", redFlat = true, blueFlat = true),
            Row("C", redFlat = false, blueFlat = false),
            Row("D", redFlat = false, blueFlat = true),
        )

        rows.forEachIndexed { index, row ->
            val position = LatLng(BASE.latitude - index * ROW_SPACING_LAT, BASE.longitude)

            // RED first (zIndex 2), anchored so it sits up-left of the point.
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(markerIcon(Color.RED, "${row.name} RED", "z=2", row.redFlat))
                    .anchor(0.65f, 0.65f)
                    .flat(row.redFlat)
                    .zIndex(2f)
            )
            // BLUE second (zIndex 1), anchored down-right so the two squares overlap.
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(markerIcon(Color.BLUE, "${row.name} BLUE", "z=1", row.blueFlat))
                    .anchor(0.35f, 0.35f)
                    .flat(row.blueFlat)
                    .zIndex(1f)
            )
        }

        // Keep the rows clear of the info panel.
        infoView.post {
            map.setPadding(0, infoView.height, 0, 0)
            val center = LatLng(BASE.latitude - 1.5 * ROW_SPACING_LAT, BASE.longitude)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
        }
    }

    private fun updateInfo() {
        val gmsVersion = runCatching {
            packageManager.getPackageInfo("com.google.android.gms", 0).let {
                "${it.versionName} (${it.longVersionCodeCompat()})"
            }
        }.getOrDefault("unknown")

        infoView.text = buildString {
            appendLine("Expected: RED (zIndex 2) on top of BLUE (zIndex 1) in every row.")
            appendLine("A: RED flat / BLUE billboard   B: both flat")
            appendLine("C: both billboard   D: RED billboard / BLUE flat")
            appendLine()
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Google Play services: $gmsVersion")
            appendLine("play-services-maps: ${BuildConfig.PLAY_SERVICES_MAPS_VERSION}")
            append("Renderer: $rendererName")
        }
    }

    private fun markerIcon(color: Int, line1: String, line2: String, flat: Boolean) =
        BitmapDescriptorFactory.fromBitmap(
            Bitmap.createBitmap(dp(64), dp(64), Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(color)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    textSize = dp(11).toFloat()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val x = bitmap.width / 2f
                canvas.drawText(line1, x, dp(19).toFloat(), paint)
                canvas.drawText(line2, x, dp(35).toFloat(), paint)
                canvas.drawText(if (flat) "flat" else "billboard", x, dp(51).toFloat(), paint)
            }
        )

    private fun dp(value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
            .toInt()

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private data class Row(val name: String, val redFlat: Boolean, val blueFlat: Boolean)

    private companion object {
        const val TAG = "FlatZIndexRepro"
        val BASE = LatLng(37.4220, -122.0841)
        const val ROW_SPACING_LAT = 0.009
    }
}
