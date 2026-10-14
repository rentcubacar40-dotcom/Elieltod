package cu.todus.download.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cu.todus.download.data.AppPreferences
import cu.todus.download.engine.PingResult
import cu.todus.download.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onTestConnection: suspend () -> PingResult,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var nationalIp by remember { mutableStateOf(preferences.nationalIp) }
    var sniHost by remember { mutableStateOf(preferences.sniHost) }
    var defaultThreads by remember { mutableIntStateOf(preferences.defaultThreads) }
    var userAgent by remember { mutableStateOf(preferences.userAgent) }
    var autoResume by remember { mutableStateOf(preferences.autoResume) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<PingResult?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 32.dp)
    ) {
        // Top Title
        Text(
            text = "Ajustes",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configuración del motor de red y tarifa cero",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Section 1: Red Nacional toDus (ETECSA Bypass)
        SettingsSectionHeader(
            title = "RED NACIONAL (TARIFA CERO)",
            icon = Icons.Default.Bolt
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "IP Nacional Fija de toDus",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "La IP directa que ETECSA factura a tarifa de red nacional (0 MB internacionales).",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nationalIp,
                    onValueChange = {
                        nationalIp = it
                        preferences.nationalIp = it
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Host / SNI TLS Fronting",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Nombre del servidor inyectado en el handshake SSL cifrado.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sniHost,
                    onValueChange = {
                        sniHost = it
                        preferences.sniHost = it
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Ping test button
                Button(
                    onClick = {
                        if (!isTestingConnection) {
                            isTestingConnection = true
                            testResult = null
                            coroutineScope.launch {
                                val res = onTestConnection()
                                testResult = res
                                isTestingConnection = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ping_test_button")
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verificando socket SSL...", color = AccentCyan, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ Probar conexión directa (Ping)",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                testResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (res.isSuccess) EmeraldSuccess.copy(alpha = 0.15f) else CrimsonError.copy(alpha = 0.15f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (res.isSuccess) "✓ ${res.message}" else "✗ ${res.message}",
                            color = if (res.isSuccess) EmeraldSuccess else CrimsonError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Motor y Rendimiento
        SettingsSectionHeader(
            title = "MOTOR Y DESCARGAS",
            icon = Icons.Default.Tune
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hilos de descarga por archivo",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Más hilos saturan mejor el ancho de banda nacional de ETECSA.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 2, 4, 8, 16).forEach { threads ->
                        val isSelected = defaultThreads == threads
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentCyan else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else SurfaceBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    defaultThreads = threads
                                    preferences.defaultThreads = threads
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "${threads}x",
                                color = if (isSelected) BgDark else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // User Agent selector
                Text(
                    text = "User-Agent HTTP",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("toDus Download", "DK ToDus-Client").forEach { ua ->
                        val isSelected = userAgent == ua
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricBlue else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) ElectricBlue else SurfaceBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    userAgent = ua
                                    preferences.userAgent = ua
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = ua,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Auto resume switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reanudación automática",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Reconectar y continuar si la señal de red fluctúa.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoResume,
                        onCheckedChange = {
                            autoResume = it
                            preferences.autoResume = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDark,
                            checkedTrackColor = AccentCyan,
                            uncheckedTrackColor = SurfaceCard,
                            uncheckedThumbColor = TextMuted
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Destino y Almacenamiento
        SettingsSectionHeader(
            title = "DESTINO DE ARCHIVOS",
            icon = Icons.Default.Folder
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Download/toDus Download/",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Indexación inmediata en Galería, VLC y reproductores del sistema.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 4: Acerca de toDus Download
        SettingsSectionHeader(
            title = "INFORMACIÓN Y BYPASS",
            icon = Icons.Default.Info
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "¿Cómo funciona la tarifa cero?",
                    color = AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "En Cuba, los paquetes de datos de ETECSA diferencian el tráfico nacional del internacional. La infraestructura de toDus opera sobre el rango de IP nacional 152.207.206.86. Al forzar la conexión directa a esta IP inyectando el SNI s3.todus.cu en el handshake TLS (SNI Fronting), el enrutador de ETECSA clasifica el consumo como red nacional sin restar megas de tu bolsa de Internet.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "toDus Download • Edición 2026",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "v1.0.0 (Compose)",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
