package com.example.arcore

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.android.filament.*
import com.google.android.filament.gltfio.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import com.google.android.filament.SwapChain


class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: GLSurfaceView
    private var arSession: Session? = null

    // Filament 元件
    private lateinit var engine: Engine
    private lateinit var scene: Scene
    private lateinit var renderer: Renderer
    private lateinit var view: View
    private lateinit var camera: com.google.android.filament.Camera
    private lateinit var swapChain: SwapChain
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private var modelAsset: FilamentAsset? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = GLSurfaceView(this)
        surfaceView.setEGLContextClientVersion(3)
        surfaceView.setRenderer(MyRenderer())
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        setContentView(surfaceView)

        if (!hasCameraPermission()) requestCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        try {
            if (arSession == null) {
                if (!ArCoreApk.getInstance().checkAvailability(this).isSupported) {
                    Toast.makeText(this, "本裝置不支援 ARCore", Toast.LENGTH_LONG).show()
                    return
                }
                arSession = Session(this)
                val config = Config(arSession)
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                arSession!!.configure(config)
            }
            arSession?.resume()
        } catch (e: UnavailableException) {
            Toast.makeText(this, "AR 啟動失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
        surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        surfaceView.onPause()
        arSession?.pause()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreate()
        } else {
            Toast.makeText(this, "需要相機權限才能使用 AR", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    inner class MyRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            engine = Engine.create()
            renderer = engine.createRenderer()
            scene = engine.createScene()
            view = engine.createView()
            swapChain = engine.createSwapChain(surfaceView.holder.surface)
            camera = engine.createCamera(engine.entityManager.create())


            view.scene = scene
            view.camera = camera

            val matProvider = UbershaderProvider(engine)
            assetLoader = AssetLoader(engine, matProvider, engine.entityManager)
            resourceLoader = ResourceLoader(engine)

            // 載入 bird.glb 模型
            val input = assets.open("bird.glb")
            val bytes = ByteArray(input.available())
            input.read(bytes)
            input.close()

            val buffer = ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
            buffer.put(bytes).rewind()

            modelAsset = assetLoader.createAsset(buffer)
            if (modelAsset == null) {
                Toast.makeText(this@MainActivity, "模型載入失敗", Toast.LENGTH_SHORT).show()
                return
            }

            resourceLoader.loadResources(modelAsset!!)
            for (entity in modelAsset!!.entities) {
                scene.addEntity(entity)
            }

            // 設定初始位置 (Z 軸往後放)
            val tm = engine.transformManager
            val ti = tm.getInstance(modelAsset!!.root)
            tm.setTransform(ti, floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, -3f,
                0f, 0f, 0f, 1f
            ))
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            camera.setProjection(
                45.0,
                width.toDouble() / height,
                0.1,
                100.0,
                com.google.android.filament.Camera.Fov.VERTICAL
            )

        }

        override fun onDrawFrame(gl: GL10?) {
            arSession?.update()

            val frameTimeNanos = System.nanoTime()
            if (::swapChain.isInitialized && renderer.beginFrame(swapChain, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }



    }
}
