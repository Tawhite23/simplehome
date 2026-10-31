package jp.kantanhome.launcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.kantanhome.launcher.data.HomeItem
import jp.kantanhome.launcher.data.HomeStore
import jp.kantanhome.launcher.data.ItemStyle
import jp.kantanhome.launcher.data.savePhotoIcon
import kotlinx.coroutines.launch

/**
 * アイコンと名前を変える。
 *  - 写真からえらぶ（連絡先なら顔写真にできる）
 *  - 色と文字でつくる（Webサイトのアイコンが見づらいとき等）
 *  - 形をそろえる／元の形のまま（アプリ・Web）
 */
@Composable
fun IconEditDialog(item: HomeItem, defaultLabel: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = HomeStore.styleOf(item.key)

    var label by remember { mutableStateOf(saved?.label ?: defaultLabel) }
    var iconFile by remember { mutableStateOf(saved?.iconFile) }
    var textIcon by remember { mutableStateOf(saved?.textIcon) }
    var color by remember { mutableStateOf(saved?.color ?: 0) }
    var shape by remember { mutableStateOf(saved?.shape) }
    var editingText by remember { mutableStateOf(saved?.textIcon != null) }

    val preview = ItemStyle(label, iconFile, textIcon, color, shape)
    val isAppLike = item is HomeItem.App || item is HomeItem.Shortcut

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val file = savePhotoIcon(context, uri)
            if (file != null) {
                iconFile = file
                textIcon = null
                editingText = false
            } else {
                Toast.makeText(context, "写真を読みこめませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BigDialog("アイコン・名前をかえる", onDismiss, dismissText = "やめる") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(wallpaperBrush(HomeStore.settings.wallpaper))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ItemIcon(item, 88.dp, preview)
                Text(label.ifBlank { defaultLabel }, style = OnWallpaper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(12) },
            label = { Text("なまえ") },
            singleLine = true,
            textStyle = TextStyle(fontSize = 20.sp),
            modifier = Modifier.fillMaxWidth(),
        )

        SubButton("写真からえらぶ") {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        SubButton("色と文字でつくる") {
            editingText = true
            if (textIcon == null) textIcon = label.ifBlank { defaultLabel }.take(1)
            iconFile = null
        }

        if (editingText && textIcon != null) {
            OutlinedTextField(
                value = textIcon.orEmpty(),
                onValueChange = { textIcon = it.take(2) },
                label = { Text("アイコンの文字（2文字まで）") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconColors.forEachIndexed { i, colors ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(colors))
                            .border(if (color == i) 4.dp else 0.dp, Ink, CircleShape)
                            .clickable { color = i },
                    )
                }
            }
        }

        if (isAppLike && iconFile == null && textIcon == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("形をそろえる", fontSize = 18.sp, color = Ink)
                    Text("オフにするとアプリの元の形のまま", fontSize = 14.sp, color = SubText)
                }
                Switch(
                    checked = shape ?: HomeStore.settings.shapeIcons,
                    onCheckedChange = { shape = it },
                )
            }
        }

        SubButton("元のアイコン・名前にもどす") {
            label = defaultLabel
            iconFile = null
            textIcon = null
            shape = null
            editingText = false
        }

        BigButton("これで決める") {
            val style = ItemStyle(
                label = label.trim().takeIf { it.isNotEmpty() && it != defaultLabel },
                iconFile = iconFile,
                textIcon = textIcon?.trim()?.takeIf { it.isNotEmpty() },
                color = color,
                shape = shape,
            )
            HomeStore.setStyle(item.key, style)
            onDismiss()
        }
        Box(Modifier.size(0.dp))
    }
}
