package com.ekomardiatno.emkeys.model

/** Pure layout and hit testing, shared by drawing, touch and accessibility. */
data class KeyBounds(val key: Key, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

object LayoutGeometry {
    fun measure(layout: Layout, width: Float, height: Float, padding: Float): List<KeyBounds> {
        if (layout.widthU <= 0f || layout.heightU <= 0f) return emptyList()
        val unitX = (width - padding * 2).coerceAtLeast(0f) / layout.widthU
        val unitY = (height - padding * 2).coerceAtLeast(0f) / layout.heightU
        var y = 0f
        return buildList {
            layout.rows.forEach { row ->
                y += row.offsetYU
                var x = row.offsetXU
                row.keys.forEach { key ->
                    x += key.gapBeforeU
                    val top = y + key.offsetYU
                    add(KeyBounds(key, padding + x * unitX, padding + top * unitY,
                        padding + (x + key.widthU) * unitX,
                        padding + (top + (key.heightU ?: row.heightU)) * unitY))
                    x += key.widthU
                }
                y += row.heightU + row.gapAfterU
            }
        }
    }
}
