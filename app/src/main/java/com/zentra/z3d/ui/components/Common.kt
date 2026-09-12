package com.zentra.z3d.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentra.z3d.ui.theme.ZentraColors

@Composable
fun ZentraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    var boxMod = modifier.clip(shape)
    boxMod = if (enabled) boxMod.background(ZentraColors.gradient, shape)
    else boxMod.background(ZentraColors.surface3, shape)
    Box(
        modifier = boxMod
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun ZentraSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(ZentraColors.surface2, shape)
            .border(1.dp, ZentraColors.surface3, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = ZentraColors.textMain, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = ZentraColors.textMain, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
    }
}

@Composable
fun ZentraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ZentraColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick ?: {}
    ) {
        // Card exige onClick no M3 1.2; quando null usamos no-op
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(vertical = 4.dp),
        color = ZentraColors.textDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

/** Botao de ferramenta grande (touch-friendly) com icone + rotulo. */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) ZentraColors.accent else Color.Transparent
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) Color.White else ZentraColors.textDim,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) Color.White else ZentraColors.textDim,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun ZentraSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { String.format("%.2f", it) }
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = ZentraColors.textMain, fontSize = 13.sp)
            Text(
                format(value),
                color = ZentraColors.accent2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ZentraColors.accent,
                activeTrackColor = ZentraColors.accent,
                inactiveTrackColor = ZentraColors.surface3
            )
        )
    }
}

@Composable
fun ZentraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = if (label.isNotEmpty()) ({ Text(label) }) else null,
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ZentraColors.accent,
            unfocusedBorderColor = ZentraColors.surface3,
            focusedTextColor = ZentraColors.textMain,
            unfocusedTextColor = ZentraColors.textMain,
            cursorColor = ZentraColors.accent2
        )
    )
}

@Composable
fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, opt ->
            FilterChip(
                selected = i == selectedIndex,
                onClick = { onSelect(i) },
                label = { Text(opt, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ZentraColors.accent,
                    selectedLabelColor = Color.White,
                    containerColor = ZentraColors.surface2,
                    labelColor = ZentraColors.textDim
                ),
                border = null
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ZentraColors.textMain) },
        text = { Text(message, color = ZentraColors.textDim) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) ZentraColors.danger else ZentraColors.accent2)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = ZentraColors.textDim) }
        },
        containerColor = ZentraColors.surface
    )
}

@Composable
fun PromptDialog(
    title: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ZentraColors.textMain) },
        text = {
            ZentraTextField(value = text, onValueChange = { text = it })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(confirmText, color = ZentraColors.accent2)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = ZentraColors.textDim) }
        },
        containerColor = ZentraColors.surface
    )
}

@Composable
fun LoadingOverlay(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ZentraColors.accent)
            Spacer(Modifier.height(12.dp))
            Text(message, color = ZentraColors.textDim, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 28.dp, onClick: (() -> Unit)? = null) {
    val mod = Modifier
        .size(size)
        .clip(CircleShape)
        .background(color, CircleShape)
        .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
    if (onClick != null) Box(mod.clickable(onClick = onClick))
    else Box(mod)
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ZentraColors.textDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
