package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Loads an image via Coil first and automatically falls back to an authenticated
 * Firebase Storage SDK download when a Firebase Storage URL fails direct HTTP fetching.
 */
@Composable
fun ResilientFirebaseImage(
    model: Any?,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    onLoadError: ((String?) -> Unit)? = null,
    loadingContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    },
    errorContent: @Composable (errorMessage: String, onRetry: () -> Unit) -> Unit = { _, onRetry ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131D2E))
                .clickable(onClick = onRetry)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = "Failed to load image",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Retry",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
) {
    val context = LocalContext.current
    var retryNonce by remember(model) { mutableIntStateOf(0) }
    var coilFailed by remember(model, retryNonce) { mutableStateOf(false) }
    var coilErrorMsg by remember(model, retryNonce) { mutableStateOf<String?>(null) }
    var fallbackBitmap by remember(model, retryNonce) { mutableStateOf<ImageBitmap?>(null) }
    var isFetchingFallback by remember(model, retryNonce) { mutableStateOf(false) }
    var fallbackError by remember(model, retryNonce) { mutableStateOf<String?>(null) }

    val modelUrl = model as? String
    val isFirebaseStorageUrl = remember(modelUrl) {
        !modelUrl.isNullOrBlank() && (
            modelUrl.startsWith("gs://", ignoreCase = true) ||
                modelUrl.contains("firebasestorage.googleapis.com", ignoreCase = true) ||
                modelUrl.contains("firebasestorage.app", ignoreCase = true)
            )
    }

    LaunchedEffect(model, coilFailed, retryNonce) {
        if (!coilFailed || !isFirebaseStorageUrl || modelUrl.isNullOrBlank()) return@LaunchedEffect
        isFetchingFallback = true
        fallbackError = null
        val fetchResult = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Firebase.storage.getReferenceFromUrl(modelUrl)
                    .getBytes(8L * 1024L * 1024L)
                    .await()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?: throw IllegalStateException("Decoded image was empty.")
            }
        }
        fetchResult.onSuccess { bmp ->
            fallbackBitmap = bmp
            fallbackError = null
            onLoadError?.invoke(null)
        }.onFailure { err ->
            val readable = formatMediaLoadError(err)
            Log.e("ResilientFirebaseImage", "Firebase SDK image fallback failed for $modelUrl: $readable", err)
            fallbackError = readable
            onLoadError?.invoke(readable)
        }
        isFetchingFallback = false
    }

    val bmp = fallbackBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
        return
    }

    val imageRequest = remember(model, retryNonce) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .apply {
                if (retryNonce > 0) {
                    memoryCachePolicy(CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.DISABLED)
                }
            }
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onSuccess = {
            coilFailed = false
            coilErrorMsg = null
            onLoadError?.invoke(null)
        },
        onError = { state ->
            val msg = formatMediaLoadError(state.result.throwable)
            Log.w("ResilientFirebaseImage", "Coil load failed for $model: $msg", state.result.throwable)
            coilFailed = true
            coilErrorMsg = msg
            if (!isFirebaseStorageUrl) {
                onLoadError?.invoke(msg)
            }
        },
        loading = { loadingContent() },
        error = {
            if (isFirebaseStorageUrl && isFetchingFallback) {
                loadingContent()
            } else {
                val displayError = fallbackError ?: coilErrorMsg ?: "Unable to load image"
                errorContent(displayError) { retryNonce++ }
            }
        }
    )
}

private fun formatMediaLoadError(error: Throwable): String {
    if (error is StorageException) {
        return when (error.errorCode) {
            StorageException.ERROR_NOT_AUTHORIZED ->
                "Storage permission denied by security rules."
            StorageException.ERROR_BUCKET_NOT_FOUND,
            StorageException.ERROR_PROJECT_NOT_FOUND ->
                "Firebase Storage bucket is not provisioned."
            StorageException.ERROR_OBJECT_NOT_FOUND ->
                "Image file no longer exists in storage."
            else -> error.localizedMessage ?: "Storage download failed."
        }
    }
    return error.localizedMessage ?: "Network or image decode error."
}
