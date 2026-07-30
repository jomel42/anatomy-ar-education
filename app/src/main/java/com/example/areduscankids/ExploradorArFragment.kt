package com.example.areduscankids

import android.os.Bundle
import com.google.ar.core.Config
import com.google.ar.sceneform.ux.ArFragment

/** Modo "Explorar": coloca modelos tocando planos. */
class ExploradorArFragment : ArFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configura sesión con listener
        setOnSessionConfigurationListener { _, config ->
            // Planos + Depth + Instant Placement: mejor en superficies lisas (azulejos blancos)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.depthMode = Config.DepthMode.AUTOMATIC
            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP

            // Estable y con menos costo
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.focusMode = Config.FocusMode.AUTO
        }
    }
}
