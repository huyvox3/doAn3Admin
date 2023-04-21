package com.example.ecommerceadminapp

import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.ecommerceadminapp.databinding.ActivityMainBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog

import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private var selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()

    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.colorPickerBtn.setOnClickListener{
           ColorPickerDialog.Builder(this)
               .setTitle("Product color")
               .setPositiveButton("Select",object: ColorEnvelopeListener{
                   override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                       envelope?.let {
                           selectedColors.add(it.color)
                           updateColors()
                       }
                   }
               })
               .setNegativeButton("Cancel"){colorPicker, _ ->
                   colorPicker.dismiss()
               }.show()
        }

        val selectedImagesActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == RESULT_OK){
                val intent = it.data

                if (intent?.clipData != null){
                    val count = intent.clipData?.itemCount ?:0
                    (0 until count).forEach{imgPos ->
                        val imageUri = intent.clipData?.getItemAt(imgPos)?.uri
                        imageUri?.let {img->
                            selectedImages.add(img)
                        }
                    }
                }
                else{
                    val imageUri = intent?.data
                    imageUri?.let {img ->
                        selectedImages.add(img)

                    }
                }
                updateImage()
            }

        }

        binding.imagePickerBtn.setOnClickListener{
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            intent.type = "image/*"
            selectedImagesActivityResult.launch(intent)
        }
        setContentView(binding.root)
    }

    private fun updateImage() {
        binding.selectedImagesTv.text = selectedImages.size.toString()
    }

    private fun updateColors() {
        var colors = ""
        selectedColors.forEach{
            colors = "$colors ${Integer.toHexString(it)}"

        }
        binding.selectedColorsTv.text = colors
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.saveProduct){
            val productValidation = validateInformation()

            if (!productValidation){
                Toast.makeText(this,"Check your input",Toast.LENGTH_SHORT).show()

                return false
            }
            saveProduct()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveProduct() {
        val name = binding.nameEt.text.toString()
        val category = binding.categoryEt.text.toString()
        val price = binding.priceEt.text.toString()
        val offerp = binding.offerpEt.text.toString()
        val des = binding.desEt.text.toString()
        val size = getSizeList(binding.edSizes.text.toString())
        val quantity = binding.quantityEt.text.toString()

        val imagesByteArrays = getImagesByteArrays()
        val images = mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.IO){
            withContext(Dispatchers.IO){
                showLoading()
            }


            try {
                async {
                    imagesByteArrays.forEach{
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imagesStorage =productStorage.child("products/images/$id")
                            val result = imagesStorage.putBytes(it).await()
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()

            }catch (e:Exception){
                e.printStackTrace()
                withContext(Dispatchers.IO){
                    hideLoading()
                }

            }

            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toInt(),
                if (offerp.isEmpty()) null else offerp.toFloat(),
                if(des.isEmpty()) null else des,
                if (selectedColors.isEmpty()) null else selectedColors,
                size,
                quantity,
                images,


            )
            firestore.collection("Products").add(product).addOnSuccessListener {
                hideLoading()
                clearData()
                Toast.makeText(this@MainActivity,"Product Added",Toast.LENGTH_SHORT).show()

            }.addOnFailureListener{
                hideLoading()

                Log.e("Error",it.message.toString())
            }
        }
    }

    private fun showLoading() {
//        binding.progressbar.visibility = View.VISIBLE
        Log.e("Progress Bar", "SHOW")
    }

    private fun hideLoading() {
//        binding.progressbar.visibility = View.INVISIBLE

        Log.e("Progress Bar", "HIDE")
    }

    private fun clearData(){
        binding.nameEt.text = null
        binding.categoryEt.text = null
        binding.desEt.text = null
        binding.offerpEt.text = null
        binding.priceEt.text = null
        binding.quantityEt.text = null
        binding.edSizes.text = null

        selectedImages.clear()
        selectedColors.clear()

        binding.selectedColorsTv.text = null
        binding.selectedImagesTv.text = null

    }
    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArray = mutableListOf<ByteArray>()
        selectedImages.forEach{
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(contentResolver,it)
            if (imageBmp.compress(Bitmap.CompressFormat.JPEG,100,stream)){
                imagesByteArray.add(stream.toByteArray())
            }
        }

        return imagesByteArray
    }

    private fun getSizeList(sizes: String): List<String>? {
        if (sizes.isEmpty()){
            return null
        }
        val sizeList = sizes.split(",")
        return sizeList
    }

    private fun validateInformation(): Boolean {
        if (binding.priceEt.toString().trim().isEmpty()){
            return false
        }
        if (binding.nameEt.toString().trim().isEmpty()){
            return false
        }
        if (binding.categoryEt.toString().trim().isEmpty()){
            return false
        }
        if (selectedImages.isEmpty()){
            return false
        }
        return true

    }
}