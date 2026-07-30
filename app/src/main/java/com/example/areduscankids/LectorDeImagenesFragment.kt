package com.example.areduscankids

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.ux.ArFragment


class LectorDeImagenesFragment : ArFragment(), Scene.OnUpdateListener {

    data class Target(val name: String, val assetPath: String, val widthM: Float)

    var targets: List<Target> = emptyList()

    var imgdbAssetName: String? = null

    interface OnImageEventListener {
        fun onImageTracking(image: AugmentedImage) {}
        fun onImageStopped(image: AugmentedImage) {}
    }
    var imageEventListener: OnImageEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setOnSessionConfigurationListener { session, config ->
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode  = Config.FocusMode.AUTO
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.augmentedImageDatabase = buildDb(session)
        }
    }

    private fun buildDb(session: Session): AugmentedImageDatabase {
        // Opción .imgdb (deserializa BD precompilada desde assets/)
        imgdbAssetName?.let { file ->
            requireContext().assets.open(file).use {
                return AugmentedImageDatabase.deserialize(session, it)
            }
        }

        // Opción runtime: crea la BD desde PNG/JPG en assets/cards
        val db = AugmentedImageDatabase(session)
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.RGB_565 // menos RAM
        }

        for (t in targets) {
            var bmp = requireContext().assets.open(t.assetPath).use {
                BitmapFactory.decodeStream(it, null, opts)
            }

            if (bmp == null) {
                val fallback = BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                bmp = requireContext().assets.open(t.assetPath).use {
                    BitmapFactory.decodeStream(it, null, fallback)
                }
            }

            if (bmp == null) continue

            db.addImage(t.name, bmp, t.widthM)
            bmp.recycle()
        }
        return db
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arSceneView.scene.addOnUpdateListener(this)
    }

    override fun onDestroyView() {
        arSceneView.scene.removeOnUpdateListener(this)
        super.onDestroyView()
    }

    override fun onUpdate(frameTime: FrameTime) {
        val frame = arSceneView.arFrame ?: return
        val updated = frame.getUpdatedTrackables(AugmentedImage::class.java)
        for (img in updated) {
            when (img.trackingState) {
                TrackingState.TRACKING -> imageEventListener?.onImageTracking(img)
                TrackingState.STOPPED  -> imageEventListener?.onImageStopped(img)
                else -> {}
            }
        }
    }
}
