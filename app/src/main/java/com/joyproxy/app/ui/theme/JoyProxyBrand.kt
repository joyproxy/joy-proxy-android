package com.joyproxy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JoyProxyHeader(onTitleClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JoyProxyColors.BrandGradient)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "JoyProxy",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onTitleClick),
        )
    }
}
