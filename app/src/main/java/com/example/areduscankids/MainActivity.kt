package com.example.areduscankids

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.TransformableNode

class MainActivity : AppCompatActivity(), LectorDeImagenesFragment.OnImageEventListener {

    private lateinit var txtCaso: TextView
    private lateinit var fabMenu: FloatingActionButton

    // ---- Estado / modos ----
    private enum class Modo { EXPLORAR, JUEGO }
    // Hacerlo nullable para forzar el primer cambiarModo():
    private var modoActual: Modo? = null

    // ---- Fragments ----
    private var fragExplorar: ExploradorArFragment? = null
    private var fragCartas: LectorDeImagenesFragment? = null

    // ---- Explorar (un solo escaneo) ----
    private var anchorNodeFijo: AnchorNode? = null
    private var nodoExplorarActual: TransformableNode? = null
    private var modeloSeleccionado = "corazon"
    private var anchorFijado = false

    // ---- Cartas ----
    private val nodoPorIndex = mutableMapOf<Int, AnchorNode>()

    // ---- Caché de modelos ----
    private val renderableCache = mutableMapOf<String, ModelRenderable>()
    private val loadingGlb = mutableSetOf<String>()
    private var bloqueoCargas = false

    // ---- Modelos (.glb en assets/models/) ----
    private val modelos = mapOf(
        "nervio"                 to "models/nervios.glb",
        "huesos y organos"       to "models/huesoorgano.glb",
        "huesos del cuerpo"      to "models/huesos.glb",
        "neuro"                  to "models/nerviohuso.glb",
        "organos"                to "models/organos.glb",
        "corazon"                to "models/corazon.glb",
        "cabeza"                 to "models/cabeza.glb",
        "cabeza musculos"        to "models/cabeza musculos.glb",
        "craneo"                 to "models/craneo.glb",
        "nervios"                to "models/nervios.glb",
        "costillas"              to "models/costillas.glb",
        "cerebro partes"         to "models/cerebropartes.glb",
        "columna"                to "models/columna.glb",
        "diafracma"              to "models/diafracma.glb",
        "partespecho"            to "models/partespecho.glb",
        "pelvis"                 to "models/pelvis.glb",
        "pie"                    to "models/piee.glb",
        // nuevos
        "brazo musculos"         to "models/brazo.glb",
        "estomago"               to "models/estomago.glb",
        "higado"                 to "models/higado.glb",
        "musculos del cuerpo"    to "models/musculoscuerpo.glb",
        "pulmones"               to "models/pulmones.glb",
        "riñon"                  to "models/riñon.glb",
        "cerebro parietal"       to "models/craneoparietal.glb"
    )

    // ==== CATEGORÍAS -> claves (para el selector del modo EXPLORAR) ====
    private val categoriaToKeys = mapOf(
        "HUESOS" to listOf("huesos del cuerpo", "costillas", "craneo", "columna", "pie", "cerebro parietal"),
        "ORGANOS" to listOf(
            "corazon", "organos", "partespecho", "diafracma",
            "cerebro partes", "estomago", "higado", "pulmones", "riñon", "pelvis", "cabeza"
        ),
        "MUSCULOS" to listOf("cabeza musculos", "brazo musculos", "musculos del cuerpo",
            "huesos y organos", "nervio", "nervios", "neuro")
    )

    // ---- Audios (solo se usan en EXPLORAR) ----
    private val audioPorModelo = mapOf(
        "corazon"             to R.raw.corazonhuma,
        "craneo"              to R.raw.esqueletohuman,
        "cabeza"              to R.raw.cabezamusculo,
        "cabeza musculos"     to R.raw.cabezamusculo,
        "cerebro partes"      to R.raw.cerebropartes,
        "columna"             to R.raw.columna,
        "diafracma"           to R.raw.diafracma,
        "partespecho"         to R.raw.partespecho,
        "pelvis"              to R.raw.pelvis,
        "pie"                 to R.raw.pie,
        // "hueso" apuntará a "huesos del cuerpo"
        "huesos del cuerpo"   to R.raw.esqueletohuman,
        "huesos y organos"    to R.raw.esqueletoorganos,
        "organos"             to R.raw.organos,
        "neuro"               to R.raw.sistemanervioso,
        "nervios"             to R.raw.sistemanervioso,
        "nervio"              to R.raw.nerviomasesqueleto,
        "costillas"           to R.raw.esqueletohuman,
        "brazo musculos"      to R.raw.brazo,
        "estomago"            to R.raw.estomago,
        "higado"              to R.raw.higado,
        "musculos del cuerpo" to R.raw.musculoscuerpo,
        "pulmones"            to R.raw.pulmones,
        "riñon"               to R.raw.rinon
    )

    // ---- Audio Player (solo explorar) ----
    private var mediaPlayer: MediaPlayer? = null
    private fun reproducirAudioModelo(key: String) {
        mediaPlayer?.release()
        mediaPlayer = null
        val resId = audioPorModelo[key] ?: return
        mediaPlayer = MediaPlayer.create(this, resId).apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    // ===== Ciclo de vida =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtCaso  = findViewById(R.id.txtCaso)
        fabMenu  = findViewById(R.id.fabMenu)
        fabMenu.setOnClickListener { mostrarMenuFlotante(it) }

        pedirPermisoCamara {
            // Forzar primer cambio de modo
            cambiarModo(Modo.JUEGO)
        }
    }

    // ===== Permisos =====
    private fun pedirPermisoCamara(onOk: () -> Unit) {
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (ok) onOk()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cambiarModo(Modo.JUEGO)
        } else {
            Toast.makeText(this, "Se necesita la cámara.", Toast.LENGTH_LONG).show()
        }
    }


    // ===== Menú  =====
    private fun mostrarMenuFlotante(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menu.add(0, 1, 0, "Huesos")
        popup.menu.add(0, 2, 1, "Órganos")
        popup.menu.add(0, 3, 2, "Músculos")
        popup.menu.add(0, 4, 3, "Juego de cartas")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { irAExplorarYElegir("HUESOS"); true }
                2 -> { irAExplorarYElegir("ORGANOS"); true }
                3 -> { irAExplorarYElegir("MUSCULOS"); true }
                4 -> { cambiarModo(Modo.JUEGO); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun irAExplorarYElegir(categoria: String) {
        cambiarModo(Modo.EXPLORAR)
        txtCaso.text = if (!anchorFijado)
            "Explorar: toca una vez el plano para fijar el modelo."
        else
            "Explorar: modelo fijo. Usa el menú para cambiarlo."
        mostrarSelectorDeModeloPorCategoria(categoria)
    }

    // ===== Cambiar de modo =====
    private fun cambiarModo(nuevo: Modo) {
        if (modoActual == nuevo && supportFragmentManager.findFragmentById(R.id.arFragmentContainer) != null) {
            // Ya hay algo cargado en ese modo
            return
        }
        modoActual = nuevo
        when (nuevo) {
            Modo.EXPLORAR -> {
                // Limpia modo cartas
                limpiarCartas()

                fragExplorar = ExploradorArFragment()
                supportFragmentManager.commit { replace(R.id.arFragmentContainer, fragExplorar!!) }
                fragExplorar!!.viewLifecycleOwnerLiveData.observe(this) { owner ->
                    if (owner != null) {
                        setupExplorarListeners()
                        fragExplorar!!.viewLifecycleOwnerLiveData.removeObservers(this)
                    }
                }
                txtCaso.text = if (anchorFijado)
                    "Explorar: modelo fijo. Usa el menú para cambiarlo."
                else
                    "Explorar: toca una vez el plano para colocar '$modeloSeleccionado'."
            }
            Modo.JUEGO -> {
                txtCaso.text = "Juego: apunta la cámara a una carta (10 cm)."

                // Limpia modo explorar
                anchorNodeFijo?.let { it.parent?.removeChild(it); it.anchor?.detach() }
                anchorNodeFijo = null
                nodoExplorarActual = null
                anchorFijado = false

                // === TUS CARDS (como en assets/cards/) ===
                fragCartas = LectorDeImagenesFragment().apply {
                    targets = listOf(
                        LectorDeImagenesFragment.Target("brazo_musculo",          "cards/brazo_musculo.png",           0.10f),
                        LectorDeImagenesFragment.Target("cabeza",                 "cards/cabeza.png",                  0.10f),
                        LectorDeImagenesFragment.Target("cabeza_musculo",         "cards/cabeza_musculo.png",          0.10f),
                        LectorDeImagenesFragment.Target("cerebro",                "cards/cerebro.png",                 0.10f),
                        LectorDeImagenesFragment.Target("cerebro_partes",         "cards/cerebro_partes.png",          0.10f),
                        LectorDeImagenesFragment.Target("columna_vertebral",      "cards/columna_vertebral.png",       0.10f),
                        LectorDeImagenesFragment.Target("corazon",                "cards/corazon.png",                 0.10f),
                        LectorDeImagenesFragment.Target("costillas",              "cards/costillas.png",               0.10f),
                        LectorDeImagenesFragment.Target("craneo",                 "cards/craneo.png",                  0.10f),
                        LectorDeImagenesFragment.Target("diafracma",              "cards/diafracma.png",               0.10f),
                        LectorDeImagenesFragment.Target("estomago",               "cards/estomago.png",                0.10f),
                        LectorDeImagenesFragment.Target("higado",                 "cards/higado.png",                  0.10f),
                        LectorDeImagenesFragment.Target("hueso_parietal",         "cards/hueso_parietal.png",          0.10f),
                        LectorDeImagenesFragment.Target("huesos",                 "cards/huesos.png",                  0.10f),
                        LectorDeImagenesFragment.Target("huesos_organos",         "cards/huesos_organos.png",          0.10f),
                        LectorDeImagenesFragment.Target("musculos_cuerpo",        "cards/musculos_cuerpo.png",         0.10f),
                        LectorDeImagenesFragment.Target("nervio_cuerpo_completo", "cards/nervio_cuerpo_completo.png",  0.10f),
                        LectorDeImagenesFragment.Target("nervios",                "cards/nervios.png",                 0.10f),
                        LectorDeImagenesFragment.Target("organos",                "cards/organos.png",                 0.10f),
                        LectorDeImagenesFragment.Target("partes_pecho",           "cards/partes_pecho.png",            0.10f),
                        LectorDeImagenesFragment.Target("pelvis",                 "cards/pelvis.png",                  0.10f),
                        LectorDeImagenesFragment.Target("pie",                    "cards/pie.png",                     0.10f),
                        LectorDeImagenesFragment.Target("pulmones",               "cards/pulmones.png",                0.10f),
                        LectorDeImagenesFragment.Target("riñon",                  "cards/riñon.png",                   0.10f),
                        LectorDeImagenesFragment.Target("traquea",                "cards/traquea.png",                 0.10f)
                    )
                    imageEventListener = this@MainActivity
                }

                replaceFragment(fragCartas!!)
            }
        }
    }

    private fun replaceFragment(f: Fragment) {
        supportFragmentManager.commit { replace(R.id.arFragmentContainer, f) }
    }

    private fun limpiarCartas() {
        // Quita nodos anclados por imágenes
        val scene = fragCartas?.arSceneView?.scene ?: return
        nodoPorIndex.values.forEach { node ->
            scene.removeChild(node)
            node.anchor?.detach()
        }
        nodoPorIndex.clear()
    }

    // ===== Listeners de EXPLORAR (un solo escaneo) =====
    private fun setupExplorarListeners() {
        val f = fragExplorar ?: return
        val sceneView = f.arSceneView ?: return

        sceneView.planeRenderer.isEnabled = !anchorFijado

        f.setOnTapArPlaneListener { hit: HitResult, plane: Plane, _ ->
            if (!anchorFijado && plane.trackingState == TrackingState.TRACKING) {
                placeFixedAnchor(hit.createAnchor())
                anchorFijado = true
                sceneView.planeRenderer.isEnabled = false
                f.setOnTapArPlaneListener(null)
                sceneView.setOnTouchListener(null)
                txtCaso.text = "Explorar: modelo fijo. Usa el menú para cambiarlo."
            } else if (anchorFijado) {
                modelos[modeloSeleccionado]?.let { ruta -> reemplazarModeloEnPuntoFijo(ruta) }
            }
        }

        sceneView.setOnTouchListener { _, ev ->
            if (!anchorFijado) return@setOnTouchListener false
            ev.action == MotionEvent.ACTION_UP
        }
    }

    // ===== Selector por CATEGORÍA (EXPLORAR) =====
    private fun mostrarSelectorDeModeloPorCategoria(categoria: String) {
        val claves = categoriaToKeys[categoria] ?: emptyList()
        val modelosValidos = claves.filter { modelos.containsKey(it) }
        if (modelosValidos.isEmpty()) {
            Toast.makeText(this, "No hay modelos en $categoria.", Toast.LENGTH_LONG).show()
            return
        }
        val nombres = modelosValidos.toTypedArray()
        val idx = nombres.indexOf(modeloSeleccionado).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Elige ${categoria.lowercase()}")
            .setSingleChoiceItems(nombres, idx) { _, which ->
                modeloSeleccionado = nombres[which]
            }
            .setPositiveButton("OK") { dialog, _ ->
                if (anchorFijado) {
                    modelos[modeloSeleccionado]?.let { ruta -> reemplazarModeloEnPuntoFijo(ruta) }
                } else {
                    txtCaso.text = "Explorar: toca una vez el plano para colocar '$modeloSeleccionado'."
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ===== Punto fijo (Explorar) =====
    private fun placeFixedAnchor(anchor: Anchor) {
        anchorNodeFijo?.let { it.parent?.removeChild(it); it.anchor?.detach() }
        nodoExplorarActual = null

        anchorNodeFijo = AnchorNode(anchor).also {
            it.setParent(fragExplorar!!.arSceneView.scene)
        }

        modelos[modeloSeleccionado]?.let { ruta ->
            cargarRenderable(ruta) { r -> colocarModeloFijo(anchorNodeFijo!!, r) }
        }
        // En EXPLORAR sí suena el audio
        reproducirAudioModelo(modeloSeleccionado)
    }

    private fun colocarModeloFijo(anchorNode: AnchorNode, r: ModelRenderable) {
        val tn = TransformableNode((fragExplorar ?: fragCartas)!!.transformationSystem).apply {
            renderable = r
            setParent(anchorNode)
            select()
        }
        nodoExplorarActual = tn
    }

    private fun reemplazarModeloEnPuntoFijo(glbPath: String) {
        val an = anchorNodeFijo ?: return
        cargarRenderable(glbPath) { r ->
            val node = nodoExplorarActual
            if (node != null) {
                val prevScale: Vector3 = node.worldScale
                val prevRot: Quaternion = node.worldRotation
                node.renderable = r
                node.worldScale = prevScale
                node.worldRotation = prevRot
                node.select()
            } else {
                colocarModeloFijo(an, r)
            }
            // En EXPLORAR sí suena el audio
            reproducirAudioModelo(modeloSeleccionado)
        }
    }

    // ===== Carga con caché =====
    private fun cargarRenderable(glbPath: String, onReady: (ModelRenderable) -> Unit) {
        renderableCache[glbPath]?.let { onReady(it); return }
        if (!loadingGlb.add(glbPath)) return
        bloqueoCargas = true

        ModelRenderable.builder()
            .setSource(this, Uri.parse(glbPath))
            .setIsFilamentGltf(true)
            .setRegistryId(glbPath)
            .build()
            .thenAccept { r ->
                r.isShadowCaster = false
                r.isShadowReceiver = false
                renderableCache[glbPath] = r
                onReady(r)
            }
            .whenComplete { _, _ ->
                loadingGlb.remove(glbPath)
                bloqueoCargas = false
            }
            .exceptionally {
                Toast.makeText(this, "No pude cargar: $glbPath", Toast.LENGTH_LONG).show()
                null
            }
    }

    // ====== Mapeo: nombre de card -> key de modelo ======
    private val cardToModelKey = mapOf(
        "brazo_musculo"          to "brazo musculos",
        "cabeza"                 to "cabeza",
        "cabeza_musculo"         to "cabeza musculos",
        "cerebro_partes"         to "cerebro partes",
        "columna_vertebral"      to "columna",
        "corazon"                to "corazon",
        "costillas"              to "costillas",
        "craneo"                 to "craneo",
        "diafracma"              to "diafracma",
        "estomago"               to "estomago",
        "higado"                 to "higado",
        "hueso_parietal"         to "cerebro parietal",
        "huesos"                 to "huesos del cuerpo",
        "huesos_organos"         to "huesos y organos",
        "musculos_cuerpo"        to "musculos del cuerpo",
        "nervio_cuerpo_completo" to "neuro",
        "nervios"                to "nervios",
        "organos"                to "organos",
        "partes_pecho"           to "partespecho",
        "pelvis"                 to "pelvis",
        "pie"                    to "pie",
        "pulmones"               to "pulmones",
        "riñon"                  to "riñon"

    )


    // ===== Modo Cartas: modelo pequeño y pegado a la carta; sin audio =====
    override fun onImageTracking(image: AugmentedImage) {
        if (image.trackingState != TrackingState.TRACKING) return
        if (nodoPorIndex.containsKey(image.index)) return
        if (bloqueoCargas) return

        val cardName = image.name
        val modelKey = cardToModelKey[cardName] ?: run {
            Toast.makeText(this, "No hay modelo asignado para '$cardName'", Toast.LENGTH_SHORT).show()
            return
        }
        val glbPath = modelos[modelKey] ?: run {
            Toast.makeText(this, "No hay GLB para '$modelKey'", Toast.LENGTH_SHORT).show()
            return
        }

        cargarRenderable(glbPath) { render ->
            val anchor = image.createAnchor(image.centerPose)
            val anchorNode = AnchorNode(anchor).also {
                it.setParent(fragCartas!!.arSceneView.scene)
                nodoPorIndex[image.index] = it
            }

            // Nodo intermedio “pegado” a la carta
            val pegado = Node().apply {
                setParent(anchorNode)
                localPosition = Vector3(0f, 0.002f, 0f) // 2 mm sobre la carta
            }

            TransformableNode(fragCartas!!.transformationSystem).apply {
                renderable = render
                setParent(pegado)
                select()

                // modelo pequeño
                worldScale = Vector3(0.02f, 0.02f, 0.02f)
                translationController.isEnabled = false
                rotationController.isEnabled = true
                scaleController.isEnabled = true
                scaleController.minScale = 0.01f
                scaleController.maxScale = 0.2f
            }

            // Sin audio en modo cartas
            txtCaso.text = "Carta: $cardName → modelo '$modelKey' colocado sobre la superficie."
        }
    }

    override fun onImageStopped(image: AugmentedImage) {
        nodoPorIndex.remove(image.index)?.let { node ->
            fragCartas?.arSceneView?.scene?.removeChild(node)
            node.anchor?.detach()
        }
    }
}
