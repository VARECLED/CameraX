package com.example.camerax

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.Camera
import com.example.camerax.databinding.ActivityMainBinding
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var photoFile : File


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Validación de permisos (mantén solo los que necesitas)
        val requiredPermissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )

        if (!arePermissionsGranted(requiredPermissions)) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                1000
            )
        }


        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        val previewView: PreviewView = findViewById(R.id.preview_view)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCase(previewView, cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        binding.btnCamera.setOnClickListener(){
            tomarFoto()
        }

    }

    private fun bindCameraUseCase(previewView: PreviewView, cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder().build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()

        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

        // Habilitar el enfoque táctil en el PreviewView
        previewView.setOnTouchListener { _, event ->
            val action = event.action
            if (action == MotionEvent.ACTION_DOWN) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val actionFocus = FocusMeteringAction.Builder(point).build()

                // Asegúrate de que la cámara esté en un estado válido antes de enfocar
                camera?.cameraControl?.startFocusAndMetering(actionFocus)
            }
            true
        }
    }

    private fun tomarFoto() {
        val imageCapture = imageCapture ?: return
        photoFile = createImageFile()

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    // Mostrar el diálogo para guardar o descartar la foto
                    mostrarDialogoGuardarFoto(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    mostrarDialogoError(this@MainActivity, "La foto no se capturó")
                }
            }
        )
    }

    private fun mostrarDialogoGuardarFoto(photoFile: File) {
        val dialogView = layoutInflater.inflate(R.layout.vista_previa, null)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.image_preview)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

        // Cargar la foto capturada en el ImageView de la vista previa del diálogo
        Glide.with(this)
            .load(photoFile)
            .into(imagePreview)

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setView(dialogView)

        val dialog = alertDialogBuilder.create()
        dialog.show()

        btnCancel.setOnClickListener {
            // El usuario eligió cancelar, elimina el archivo de la foto
            eliminarFoto(photoFile)
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            // El usuario eligió guardar la foto
            // Llama al método para guardar la foto
            guardarFoto(photoFile)
            binding.txtTitOpcion.setText(photoFile.toString())
            dialog.dismiss()
        }
    }

    private fun guardarFoto(photoFile: File) {
        // Agrega aquí la lógica para guardar la foto en la ubicación deseada o en el almacenamiento preferido por tu app.
        // Por ejemplo, podrías guardarla en la galería usando MediaStore o en un directorio específico en el almacenamiento interno/externo de la app.
        // Para guardar en la galería, asegúrate de tener los permisos necesarios (WRITE_EXTERNAL_STORAGE).
        // Para guardar en el almacenamiento interno de la app, simplemente muévela a la ubicación deseada utilizando los métodos de File.

        // Mostrar un mensaje de éxito al usuario
        Toast.makeText(applicationContext, "Foto guardada exitosamente", Toast.LENGTH_SHORT).show()
    }

    private fun eliminarFoto(photoFile: File) {
        // Si el usuario no quiere guardar la foto, simplemente elimina el archivo de la foto.
        // Aquí también puedes agregar lógica adicional si es necesario, como mostrar un mensaje de confirmación antes de eliminar el archivo.

        if (photoFile.exists()) {
            photoFile.delete()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(outputDirectory, "IMAGE_${timeStamp}.jpg")
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir ?: filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun arePermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    fun mostrarDialogoError(context: Context, mensaje: String) {
        val alertDialogBuilder = AlertDialog.Builder(context)

        // Configura el título y el mensaje del cuadro de diálogo
        alertDialogBuilder.setTitle("Alerta")
        alertDialogBuilder.setMessage(mensaje)

        // Configura el botón positivo del cuadro de diálogo (opcional)
        alertDialogBuilder.setPositiveButton("Aceptar", DialogInterface.OnClickListener { dialog, which ->
        })
        // Crea y muestra el cuadro de diálogo
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }
}