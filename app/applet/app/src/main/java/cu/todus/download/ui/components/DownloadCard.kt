package cu.todus.download.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cu.todus.download.data.DownloadItem
import cu.todus.download.ui.theme.*

@Composable
fun DownloadCard(
    item: DownloadItem,
    onPauseResume: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onShare: (DownloadItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}")
            .border(
                width = 1.dp,
                color = if (item.status == DownloadItem.Status.DOWNLOADING) AccentCyan.copy(alpha = 0.4f) else SurfaceBorder,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Icon + Title + Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // File Type Icon Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                ) {
                    val icon: ImageVector = when {
                        item.isVideo -> Icons.Default.Movie
                        item.isAudio -> Icons.Default.Audiotrack
                        item.isDocument -> Icons.Default.Description
                        item.isArchive -> Icons.Default.FolderZip
                        else -> Icons.Default.InsertDriveFile
                    }
                    val iconTint = when {
                        item.status == DownloadItem.Status.COMPLETED -> EmeraldSuccess
                        item.status == DownloadItem.Status.ERROR -> CrimsonError
                        else -> AccentCyan
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Tipo de archivo",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (item.destinationPath.isNotBlank()) item.destinationPath.substringAfterLast("/") else "Download/toDus Download",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stylized Gradient Progress Bar
            val animatedProgress by animateFloatAsState(
                targetValue = (item.percentage / 100f).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
                label = "progress_anim"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = when (item.status) {
                                    DownloadItem.Status.COMPLETED -> listOf(EmeraldSuccess, Color(0xFF34D399))
                                    DownloadItem.Status.ERROR -> listOf(CrimsonError, Color(0xFFFB7185))
                                    DownloadItem.Status.PAUSED -> listOf(AmberWarn, Color(0xFFFBBF24))
                                    else -> listOf(AccentCyan, ElectricBlue)
                                }
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row: size, percentage, speed, ETA, threads
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${item.humanSize(item.downloadedBytes)} / ${item.humanSize(item.totalBytes)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (item.status == DownloadItem.Status.DOWNLOADING) {
                    val eta = item.etaString()
                    val etaPart = if (eta.isNotBlank()) " • $eta" else ""
                    Text(
                        text = "⚡ ${item.humanSpeed()} • ${item.threads} hilos$etaPart",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "${item.percentage}%",
                        color = if (item.status == DownloadItem.Status.COMPLETED) EmeraldSuccess else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Error message if any
            if (item.status == DownloadItem.Status.ERROR && !item.errorMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ ${item.errorMsg}",
                    color = CrimsonError,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item.status == DownloadItem.Status.COMPLETED) {
                    IconButton(
                        onClick = { onShare(item) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("share_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir archivo",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onOpen(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("open_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = BgDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Abrir Archivo",
                            color = BgDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onCancel(item) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CrimsonError
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("cancel_button_${item.id}")
                    ) {
                        Text(
                            text = if (item.status == DownloadItem.Status.ERROR) "Eliminar" else "Cancelar",
                            color = CrimsonError,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onPauseResume(item) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.status == DownloadItem.Status.DOWNLOADING) SurfaceCard else AccentCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("pause_resume_button_${item.id}")
                    ) {
                        val isDownloading = item.status == DownloadItem.Status.DOWNLOADING
                        Icon(
                            imageVector = if (isDownloading) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isDownloading) TextPrimary else BgDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isDownloading -> "Pausar"
                                item.status == DownloadItem.Status.ERROR -> "Reintentar"
                                else -> "Reanudar"
                            },
                            color = if (isDownloading) TextPrimary else BgDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: DownloadItem.Status) {
    val (label, bg, fg) = when (status) {
        DownloadItem.Status.DOWNLOADING -> Triple("Descargando", AccentCyan.copy(alpha = 0.15f), AccentCyan)
        DownloadItem.Status.COMPLETED -> Triple("Completado", EmeraldSuccess.copy(alpha = 0.15f), EmeraldSuccess)
        DownloadItem.Status.PAUSED -> Triple("Pausado", AmberWarn.copy(alpha = 0.15f), AmberWarn)
        DownloadItem.Status.ERROR -> Triple("Error", CrimsonError.copy(alpha = 0.15f), CrimsonError)
        DownloadItem.Status.QUEUED -> Triple("En cola", TextMuted.copy(alpha = 0.15f), TextSecondary)
        DownloadItem.Status.CONNECTING -> Triple("Conectando", ElectricBlue.copy(alpha = 0.15f), ElectricBlue)
        DownloadItem.Status.CANCELLED -> Triple("Cancelado", CrimsonError.copy(alpha = 0.15f), CrimsonError)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
