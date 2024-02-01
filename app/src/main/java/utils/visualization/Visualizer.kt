package utils.visualization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import java.lang.ref.WeakReference

object VisualizerInstance {
    var visualizer: Visualizer? = null
}

class Visualizer(mapView: MapView, fragmentContext: Context) {

    private val fragmentContextRef: WeakReference<Context> = WeakReference(fragmentContext)
    private val context: Context?
        get() = fragmentContextRef.get()

    private val annotationApi = mapView.annotations
    private val pointAnnotationManager = annotationApi.createPointAnnotationManager()
    private val lineAnnotationManager = annotationApi.createPolylineAnnotationManager()

    private val pointList: MutableMap<Long, PointAnnotation> = mutableMapOf()
    private val lineList: MutableMap<Long, PolylineAnnotation> = mutableMapOf()

    fun drawPoint(stationID: Long, lat: Double, lon: Double, @DrawableRes resourceId: Int) {
        bitmapFromDrawableRes(resourceId)?.let { bitmap ->
            val existingPoint = pointList[stationID]

            if (existingPoint != null) {
                // Update existing point
                existingPoint.point = Point.fromLngLat(lon, lat)
                existingPoint.iconImageBitmap = bitmap
                pointAnnotationManager.update(existingPoint)
            } else {
                // Create new point
                val newPointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(lon, lat))
                    .withIconImage(bitmap)

                val newPoint = pointAnnotationManager.create(newPointAnnotationOptions)
                pointList[stationID] = newPoint

                pointAnnotationManager.iconAllowOverlap = false
                pointAnnotationManager.iconPadding = 10.0
                pointAnnotationManager.addClickListener(OnPointAnnotationClickListener { pointAnnotation ->
                    Toast.makeText(
                        context,
                        "Marker at ${pointAnnotation.point.latitude()}, ${pointAnnotation.point.longitude()} clicked!",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                })
            }
        }
    }

    fun removePoint(stationID: Long) {
        val point = pointList[stationID] ?: return
        pointAnnotationManager.delete(point)
        pointList.remove(stationID)
    }

    fun drawLine(stationID: Long, points: List<Point>, colorResID: Int) {
        val existingLine = lineList[stationID]

        if (existingLine != null) {
            // Update existing line
            existingLine.points = points
            existingLine.lineColorInt = colorResID
            lineAnnotationManager.update(existingLine)
        } else {
            // Create new line
            val newPolyLineAnnotationOptions = PolylineAnnotationOptions()
                .withPoints(points)
                .withLineColor(colorResID)
                .withLineOpacity(0.5)
                .withLineWidth(10.0)

            val newLine = lineAnnotationManager.create(newPolyLineAnnotationOptions)
            lineList[stationID] = newLine
        }
    }


    fun removeLine(stationID: Long) {
        val line = lineList[stationID] ?: return
        lineAnnotationManager.delete(line)
        lineList.remove(stationID)
    }

    fun removeAllMarkers() {
        pointAnnotationManager.deleteAll()
        lineAnnotationManager.deleteAll()

        pointList.clear()
        lineList.clear()
    }

    private fun bitmapFromDrawableRes(@DrawableRes resourceId: Int) =
        convertDrawableToBitmap(context?.let { AppCompatResources.getDrawable(it, resourceId) })

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            // copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

}