package com.carlren.photoframe

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var hasCreds by remember { mutableStateOf(CredentialStore.hasCredentials(this)) }
                var creds by remember { mutableStateOf(CredentialStore.load(this)) }
                if (hasCreds && creds != null) {
                    PhotoFrameScreen(
                        creds = creds!!,
                        onExit = { finishAndRemoveTask() }
                    )
                } else {
                    LoginScreen(
                        onLoginSuccess = { c ->
                            CredentialStore.save(this, c)
                            creds = c
                            hasCreds = true
                        }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (SmpCredentials) -> Unit) {
    var host by remember { mutableStateOf(BuildConfig.SMB_DEFAULT_HOST) }
    var share by remember { mutableStateOf(BuildConfig.SMB_DEFAULT_SHARE) }
    var path by remember { mutableStateOf(BuildConfig.SMB_DEFAULT_PATH) }
    var username by remember { mutableStateOf(BuildConfig.SMB_DEFAULT_USERNAME) }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Photo Frame Setup", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Enter credentials for your SMB photo folder", fontSize = 14.sp, color = Color(0xFFAAAAAA))
                // Host / Share / Path row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        placeholder = { Text("nas.example.local") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = textFieldColors()
                    )
                    OutlinedTextField(
                        value = share,
                        onValueChange = { share = it },
                        label = { Text("Share") },
                        modifier = Modifier.weight(0.6f),
                        singleLine = true,
                        colors = textFieldColors()
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Folder path inside share") },
                    placeholder = { Text("Photos/Frame") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors()
                )
                Divider(color = Color(0xFF333333))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors()
                )

                if (error != null) {
                    Text(error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp, lineHeight = 16.sp)
                }
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            error = "Username and password required"
                            return@Button
                        }
                        isLoading = true
                        error = null
                        val creds = SmpCredentials(
                            host = host.trim(),
                            share = share.trim(),
                            path = path.trim().trim('/'),
                            username = username.trim(),
                            password = password
                        )
                        scope.launch {
                            val result = SmbPhotoRepository.testConnection(creds)
                            isLoading = false
                            if (result.isSuccess) {
                                // Even 0 photos is success — just empty frame, but connection ok
                                onLoginSuccess(creds)
                            } else {
                                val ex = result.exceptionOrNull()
                                error = when {
                                    ex?.message?.contains("LOGON_FAILURE", true) == true || ex.toString().contains("STATUS_LOGON_FAILURE", true) -> "Invalid username or password"
                                    ex?.message?.contains("unknown host", true) == true || ex.toString().contains("UnknownHost", true) -> "Host not found. Check the configured host and fallback host."
                                    ex?.message?.contains("STATUS_BAD_NETWORK_NAME", true) == true -> "SMB share not found"
                                    ex?.message?.contains("STATUS_OBJECT_NAME_NOT_FOUND", true) == true -> "Photo folder not found"
                                    ex != null -> "Connection failed. Check the settings and try again."
                                    else -> "Connection failed"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting...")
                    } else {
                        Text("Connect & Start Slideshow", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Credentials are encrypted on this device. The photo folder is checked every 60 seconds.",
                    fontSize = 11.sp,
                    color = Color(0xFF777777)
                )
            }
        }
    }
}

@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF4A90E2),
    unfocusedBorderColor = Color(0xFF555555),
    focusedLabelColor = Color(0xFFAAAAAA),
    unfocusedLabelColor = Color(0xFFAAAAAA),
    cursorColor = Color.White
)

@Composable
fun PhotoFrameScreen(creds: SmpCredentials, onExit: () -> Unit) {
    val context = LocalContext.current
    var remotePhotos by remember { mutableStateOf<List<SmbPhotoRepository.SmbPhoto>>(emptyList()) }
    var allLocalFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var displayFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showExitButton by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Auto-detect portrait vs landscape display (Portal Plus portrait vs Portal+ landscape)
    val configuration = LocalConfiguration.current
    val isPortraitDisplay = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Premium fonts
    val displayFont = FontFamily(Font(R.font.plus_jakarta_sans))
    val timeFont = FontFamily(Font(R.font.inter))
    val locationName = BuildConfig.WEATHER_LOCATION_NAME
    val timeZoneId = BuildConfig.DISPLAY_TIME_ZONE.ifBlank { TimeZone.getDefault().id }
    var locationTime by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf<Weather?>(null) }

    // Configured location's 24h clock ticker
    LaunchedEffect(timeZoneId) {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val fmt = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }
        while (true) {
            locationTime = fmt.format(Date())
            delay(1000L)
        }
    }
    // Weather poll every 15min
    LaunchedEffect(Unit) {
        while (true) {
            weather = fetchWeather()
            delay(15 * 60 * 1000L)
        }
    }

    // Initial load + periodic refresh every 60s — downloads ALL images (both orientations)
    // Filtering to portrait/landscape happens in the orientation effect below so we keep full cache.
    LaunchedEffect(creds) {
        while (true) {
            try {
                isLoading = remotePhotos.isEmpty()
                error = null
                val remotes = SmbPhotoRepository.listRemotePhotos(creds)
                remotePhotos = remotes
                if (remotes.isEmpty()) {
                    allLocalFiles = emptyList()
                    error = "No photos found in the configured folder."
                } else {
                    val files = SmbPhotoRepository.ensurePhotosCached(context, creds, remotes)
                    allLocalFiles = files
                    if (files.isEmpty() && remotes.isNotEmpty()) {
                        error = "Failed to download photos (${remotes.size} found)."
                    }
                    // displayFiles filtering handled by orientation effect; index clamp there
                }
            } catch (e: Exception) {
                error = "Refresh failed. Check the connection and settings."
            } finally {
                isLoading = false
            }
            delay(60_000L)
        }
    }

    // Orientation-aware filtering: portrait display → portrait photos only, landscape → landscape only.
    // Squares are shown on both. Re-runs when files change or device orientation changes.
    LaunchedEffect(allLocalFiles, isPortraitDisplay) {
        OrientationHelper.logDeviceInfo(context, isPortraitDisplay)
        if (allLocalFiles.isEmpty()) {
            displayFiles = emptyList()
            currentIndex = 0
            return@LaunchedEffect
        }
        val filtered = withContext(Dispatchers.IO) {
            OrientationHelper.filterByDisplayOrientation(allLocalFiles, isPortraitDisplay)
        }
        displayFiles = filtered
        if (currentIndex >= filtered.size) currentIndex = 0
        // Surface helpful message when no matching orientation photos exist
        if (filtered.isEmpty() && allLocalFiles.isNotEmpty()) {
            error = if (isPortraitDisplay) {
                "No portrait photos found – add portrait images to the folder. (${allLocalFiles.size} landscape available)"
            } else {
                "No landscape photos found – add landscape images to the folder. (${allLocalFiles.size} portrait available)"
            }
        } else if (filtered.isNotEmpty() && (error?.contains("No portrait photos") == true || error?.contains("No landscape photos") == true)) {
            error = null
        }
    }

    // Slideshow timer 10s — iterates over orientation-filtered list
    LaunchedEffect(displayFiles.size) {
        while (true) {
            delay(10_000L)
            if (displayFiles.size > 1) {
                currentIndex = (currentIndex + 1) % displayFiles.size
            }
        }
    }

    // Auto-hide the exit affordance after 4s.
    LaunchedEffect(showExitButton) {
        if (showExitButton) {
            delay(4000L)
            showExitButton = false
        }
    }

    var swipeAccum by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(displayFiles.size) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeAccum = 0f },
                    onHorizontalDrag = { _, amount -> swipeAccum += amount },
                    onDragEnd = {
                        if (kotlin.math.abs(swipeAccum) > 80f && displayFiles.isNotEmpty()) {
                            if (swipeAccum > 0) {
                                // left-to-right → next (as requested)
                                currentIndex = (currentIndex + 1) % displayFiles.size
                            } else {
                                // right-to-left → previous
                                currentIndex = (currentIndex - 1 + displayFiles.size) % displayFiles.size
                            }
                        }
                        swipeAccum = 0f
                    },
                    onDragCancel = { swipeAccum = 0f }
                )
            }
            .clickable { showExitButton = !showExitButton }
    ) {
        when {
            isLoading && displayFiles.isEmpty() && allLocalFiles.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = Color.White)
                        Text("Loading photos...", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    }
                }
            }
            displayFiles.isNotEmpty() -> {
                val file = displayFiles[currentIndex.coerceIn(0, displayFiles.size - 1)]
                AnimatedContent(
                    targetState = file,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(1100, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 1.02f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessVeryLow))
                            ) togetherWith
                            (fadeOut(animationSpec = tween(800)) +
                                scaleOut(targetScale = 1.04f, animationSpec = tween(800, easing = FastOutSlowInEasing)))
                    },
                    label = "applePhotoTransition"
                ) { f ->
                    // Apple-like Ken Burns: subtle 6% scale over 10s while photo is on screen
                    var kenTarget by remember(f) { mutableStateOf(1f) }
                    LaunchedEffect(f) {
                        // start slightly delayed so entrance scale settles first
                        kotlinx.coroutines.delay(400)
                        kenTarget = 1.06f
                    }
                    val kenScale by animateFloatAsState(
                        targetValue = kenTarget,
                        animationSpec = tween(10000, easing = LinearEasing),
                        label = "kenBurns"
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        // Display-ready files are pre-scaled once and decoded from Coil's memory cache.
                        // Crop fills every edge from the first frame; Ken Burns adds a subtle zoom.
                        val displayRequest = remember(f) {
                            ImageRequest.Builder(context)
                                .data(f)
                                .size(DisplayPhotoOptimizer.MAX_EDGE)
                                .memoryCacheKey("display-${f.name}-${f.length()}-${f.lastModified()}")
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = displayRequest,
                            contentDescription = f.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(0.dp))
                                .graphicsLayer(scaleX = kenScale, scaleY = kenScale),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                // Premium bottom status indicator — 2x size, uniform shade (no double shadow)
                val exifDate = remember(file) { getExifDate(file) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .background(Color(0x66000000), RoundedCornerShape(22.dp))
                        .padding(horizontal = 36.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "${currentIndex + 1} / ${displayFiles.size}",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = displayFont,
                            letterSpacing = 0.3.sp
                        )
                        if (exifDate != null) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                            )
                            Text(
                                exifDate,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = displayFont
                            )
                        }
                    }
                }
                // Premium top-left time + weather — 2x size, uniform shade (no outer ring)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 24.dp)
                        .background(Color(0x66000000), RoundedCornerShape(22.dp))
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            locationTime.ifEmpty { "--:--" },
                            color = Color.White,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = timeFont,
                            letterSpacing = 0.5.sp,
                            lineHeight = 54.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (weather != null) {
                                Text(
                                    weather!!.icon,
                                    fontSize = 26.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    "${weather!!.tempC}°C",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = displayFont
                                )
                            }
                            if (locationName.isNotBlank()) {
                                Text(
                                    locationName,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 24.sp,
                                    fontFamily = displayFont
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
                        Text("📷", fontSize = 48.sp)
                        Text(error ?: "No photos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (allLocalFiles.isNotEmpty() && displayFiles.isEmpty()) {
                                if (isPortraitDisplay) "Add portrait photos to see them here" else "Add landscape photos to see them here"
                            } else "Check the configured SMB photo folder.",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                        if (allLocalFiles.isNotEmpty()) {
                            Text(
                                "${displayFiles.size} of ${allLocalFiles.size} photos match ${if (isPortraitDisplay) "portrait" else "landscape"} display",
                                color = Color(0xFF666666),
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val remotes = SmbPhotoRepository.listRemotePhotos(creds)
                                    remotePhotos = remotes
                                    allLocalFiles = SmbPhotoRepository.ensurePhotosCached(context, creds, remotes)
                                } catch (e: Exception) { error = e.message }
                                isLoading = false
                            }
                        }) { Text("Retry") }
                    }
                }
            }
        }

        // A tap reveals one unambiguous action: close the photo frame.
        if (showExitButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(64.dp)
                    .background(Color(0x99000000), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(26.dp)) {
                    val inset = 3.dp.toPx()
                    val stroke = 3.dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = androidx.compose.ui.geometry.Offset(inset, inset),
                        end = androidx.compose.ui.geometry.Offset(size.width - inset, size.height - inset),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = androidx.compose.ui.geometry.Offset(size.width - inset, inset),
                        end = androidx.compose.ui.geometry.Offset(inset, size.height - inset),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Decode the next display-sized file before the slideshow advances.
        if (displayFiles.size > 1) {
            val nextFile = displayFiles[(currentIndex + 1) % displayFiles.size]
            LaunchedEffect(nextFile) {
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(nextFile)
                        .size(DisplayPhotoOptimizer.MAX_EDGE)
                        .memoryCacheKey(
                            "display-${nextFile.name}-${nextFile.length()}-${nextFile.lastModified()}"
                        )
                        .build()
                )
            }
        }
    }
}

private data class Weather(val icon: String, val tempC: Int)

private fun getExifDate(file: File): String? {
    return try {
        val exif = ExifInterface(file.absolutePath)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val exifFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val date = exifFmt.parse(raw) ?: return null
        val outFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
        outFmt.format(date)
    } catch (_: Exception) { null }
}

private suspend fun fetchWeather(): Weather? = withContext(Dispatchers.IO) {
    val latitude = BuildConfig.WEATHER_LATITUDE
    val longitude = BuildConfig.WEATHER_LONGITUDE
    if (latitude.isBlank() || longitude.isBlank()) return@withContext null
    runCatching {
        val timeZone = URLEncoder.encode(BuildConfig.DISPLAY_TIME_ZONE.ifBlank { "auto" }, "UTF-8")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&temperature_unit=celsius&timezone=$timeZone"
        val json = JSONObject(readUrl(url))
        val cur = json.getJSONObject("current_weather")
        val temp = cur.getDouble("temperature").toInt()
        val code = cur.getInt("weathercode")
        Weather(icon = weatherIconFor(code), tempC = temp)
    }.onFailure { Log.w("PhotoFrame", "Weather fetch failed (${it.javaClass.simpleName})") }.getOrNull()
}

private fun readUrl(url: String): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "PortalPhotoFrame/1.0")
    }
    return conn.inputStream.bufferedReader().use { it.readText() }
}

private fun weatherIconFor(code: Int): String = when (code) {
    0 -> "☀"
    1, 2 -> "⛅"
    3 -> "☁"
    45, 48 -> "🌫"
    51, 53, 55, 56, 57 -> "🌦"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧"
    71, 73, 75, 77, 85, 86 -> "❄"
    95, 96, 99 -> "⛈"
    else -> "•"
}
