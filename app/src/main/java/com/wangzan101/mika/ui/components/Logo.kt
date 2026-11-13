package com.wangzan101.mika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.wangzan101.mika.ui.identity.MikaMark
import com.wangzan101.mika.ui.theme.MikaTheme

/** Shared compact identity, including in the floating capsule. */
@Composable
fun MikaLogo(size: Dp, modifier: Modifier = Modifier, corner: Dp = size * 0.28f) {
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(MikaTheme.colors.surface),
        contentAlignment = Alignment.Center) {
        MikaMark(Modifier.fillMaxSize(0.8f))
    }
}
