package com.marcoslorcar.clementime.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PageInfoFilled: ImageVector
    get() {
        if (_pageInfoFilled != null) {
            return _pageInfoFilled!!
        }
        _pageInfoFilled = ImageVector.Builder(
            name = "PageInfoFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15.09f, 19.16f)
                quadTo(14f, 18.08f, 14f, 16.5f)
                reflectiveQuadToRelative(1.09f, -2.66f)
                reflectiveQuadToRelative(2.66f, -1.09f)
                reflectiveQuadToRelative(2.66f, 1.09f)
                quadToRelative(1.09f, 1.09f, 1.09f, 2.66f)
                reflectiveQuadToRelative(-1.09f, 2.66f)
                reflectiveQuadToRelative(-2.66f, 1.09f)
                quadToRelative(-1.57f, 0f, -2.66f, -1.09f)
                close()
                moveTo(4f, 17.5f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                close()
                moveTo(3.59f, 10.16f)
                quadTo(2.5f, 9.07f, 2.5f, 7.5f)
                reflectiveQuadTo(3.59f, 4.84f)
                reflectiveQuadTo(6.25f, 3.75f)
                reflectiveQuadTo(8.91f, 4.84f)
                quadTo(10f, 5.93f, 10f, 7.5f)
                reflectiveQuadTo(8.91f, 10.16f)
                quadTo(7.83f, 11.25f, 6.25f, 11.25f)
                reflectiveQuadTo(3.59f, 10.16f)
                close()
                moveTo(12f, 8.5f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(12f)
                close()
            }
        }.build()
        return _pageInfoFilled!!
    }

private var _pageInfoFilled: ImageVector? = null

val PageInfoOutlined: ImageVector
    get() {
        if (_pageInfoOutlined != null) {
            return _pageInfoOutlined!!
        }
        _pageInfoOutlined = ImageVector.Builder(
            name = "PageInfoOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15.09f, 19.16f)
                quadTo(14f, 18.08f, 14f, 16.5f)
                reflectiveQuadToRelative(1.09f, -2.66f)
                reflectiveQuadToRelative(2.66f, -1.09f)
                reflectiveQuadToRelative(2.66f, 1.09f)
                quadToRelative(1.09f, 1.09f, 1.09f, 2.66f)
                reflectiveQuadToRelative(-1.09f, 2.66f)
                reflectiveQuadToRelative(-2.66f, 1.09f)
                quadToRelative(-1.57f, 0f, -2.66f, -1.09f)
                close()
                moveToRelative(3.9f, -1.43f)
                quadTo(19.5f, 17.23f, 19.5f, 16.5f)
                quadToRelative(0f, -0.72f, -0.51f, -1.24f)
                reflectiveQuadTo(17.75f, 14.75f)
                quadToRelative(-0.72f, 0f, -1.24f, 0.51f)
                quadTo(16f, 15.78f, 16f, 16.5f)
                reflectiveQuadToRelative(0.51f, 1.24f)
                quadToRelative(0.51f, 0.51f, 1.24f, 0.51f)
                quadToRelative(0.73f, 0f, 1.24f, -0.51f)
                close()
                moveTo(4f, 17.5f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                close()
                moveTo(3.59f, 10.16f)
                quadTo(2.5f, 9.07f, 2.5f, 7.5f)
                reflectiveQuadTo(3.59f, 4.84f)
                reflectiveQuadTo(6.25f, 3.75f)
                reflectiveQuadTo(8.91f, 4.84f)
                quadTo(10f, 5.93f, 10f, 7.5f)
                reflectiveQuadTo(8.91f, 10.16f)
                quadTo(7.83f, 11.25f, 6.25f, 11.25f)
                reflectiveQuadTo(3.59f, 10.16f)
                close()
                moveTo(7.49f, 8.74f)
                quadTo(8f, 8.23f, 8f, 7.5f)
                quadTo(8f, 6.77f, 7.49f, 6.26f)
                reflectiveQuadTo(6.25f, 5.75f)
                reflectiveQuadTo(5.01f, 6.26f)
                reflectiveQuadTo(4.5f, 7.5f)
                reflectiveQuadTo(5.01f, 8.74f)
                reflectiveQuadTo(6.25f, 9.25f)
                reflectiveQuadTo(7.49f, 8.74f)
                close()
                moveTo(12f, 8.5f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(12f)
                close()
                moveToRelative(5.75f, 8f)
                close()
                moveTo(6.25f, 7.5f)
                close()
            }
        }.build()
        return _pageInfoOutlined!!
    }

private var _pageInfoOutlined: ImageVector? = null

val ViewWeekFilled: ImageVector
    get() {
        if (_viewWeekFilled != null) {
            return _viewWeekFilled!!
        }
        _viewWeekFilled = ImageVector.Builder(
            name = "ViewWeekFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineTo(5.33f)
                quadTo(6.15f, 4f, 6.74f, 4.59f)
                quadTo(7.33f, 5.18f, 7.33f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(5.33f, 20f)
                horizontalLineTo(4f)
                close()
                moveToRelative(7.35f, 0f)
                quadTo(10.53f, 20f, 9.94f, 19.41f)
                reflectiveQuadTo(9.35f, 18f)
                verticalLineTo(6f)
                quadToRelative(0f, -0.82f, 0.59f, -1.41f)
                reflectiveQuadTo(11.35f, 4f)
                horizontalLineToRelative(1.32f)
                quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                quadTo(14.68f, 5.18f, 14.68f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(12.68f, 20f)
                horizontalLineTo(11.35f)
                close()
                moveToRelative(7.32f, 0f)
                quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
                quadTo(16.68f, 18.83f, 16.68f, 18f)
                verticalLineTo(6f)
                quadToRelative(0f, -0.82f, 0.59f, -1.41f)
                reflectiveQuadTo(18.68f, 4f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 5.18f, 22f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(18.68f)
                close()
            }
        }.build()
        return _viewWeekFilled!!
    }

private var _viewWeekFilled: ImageVector? = null

val ViewWeekOutlined: ImageVector
    get() {
        if (_viewWeekOutlined != null) {
            return _viewWeekOutlined!!
        }
        _viewWeekOutlined = ImageVector.Builder(
            name = "ViewWeekOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(4f, 18f)
                horizontalLineTo(8f)
                verticalLineTo(6f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                close()
                moveToRelative(6f, 0f)
                horizontalLineToRelative(4f)
                verticalLineTo(6f)
                horizontalLineTo(10f)
                verticalLineTo(18f)
                close()
                moveToRelative(6f, 0f)
                horizontalLineToRelative(4f)
                verticalLineTo(6f)
                horizontalLineTo(16f)
                verticalLineTo(18f)
                close()
                moveTo(4f, 20f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 5.18f, 22f, 6f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 20f)
                horizontalLineTo(4f)
                close()
            }
        }.build()
        return _viewWeekOutlined!!
    }

private var _viewWeekOutlined: ImageVector? = null
