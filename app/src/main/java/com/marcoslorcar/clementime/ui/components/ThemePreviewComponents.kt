package com.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppSkeletonPreview(
    name: String,
    colorScheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialTheme(colorScheme = colorScheme) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .width(120.dp)
                .clickable { onClick() }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Box(modifier = Modifier.width(40.dp).height(8.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(4.dp)))
                        }
                    }
                    
                    // Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.surface),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { i ->
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(4.dp)
                                    .background(
                                        if (i == 1) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }

                    // Content Area
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Mock Schedule Cards
                            repeat(3) { i ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (i == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(5.dp)
                                                .background(
                                                    if (i == 1) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f),
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        
                        // FAB
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.onPrimaryContainer, CircleShape))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
