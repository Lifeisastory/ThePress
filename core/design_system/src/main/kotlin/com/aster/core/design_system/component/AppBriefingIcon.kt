package com.aster.core.design_system.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 文档与文本行组成的简报图标 */
val AppBriefingIcon: ImageVector by lazy {
    ImageVector.Builder("Briefing", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
            moveTo(5f, 2f); lineTo(15f, 2f); lineTo(21f, 8f); lineTo(21f, 20f)
            quadTo(21f, 22f, 19f, 22f); lineTo(5f, 22f); quadTo(3f, 22f, 3f, 20f)
            lineTo(3f, 4f); quadTo(3f, 2f, 5f, 2f); close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
            moveTo(15f, 2f); lineTo(15f, 8f); lineTo(21f, 8f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            moveTo(7f, 12f); lineTo(17f, 12f)
            moveTo(7f, 15f); lineTo(17f, 15f)
            moveTo(7f, 18f); lineTo(13f, 18f)
        }
    }.build()
}
