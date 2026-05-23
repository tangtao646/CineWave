package com.example.kmp_demo.features.film.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kmp_demo.core.components.shimmer
import com.example.kmp_demo.core.components.skeletonAspectRatio

@Composable
fun MovieSkeletonItem() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // 封面图骨架 — 使用平台相关的宽高比
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(skeletonAspectRatio())
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .shimmer()
            )

            // 文字信息骨架
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(18.dp)
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .shimmer()
                )
            }
        }
    }
}

@Composable
fun MovieDetailSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Backdrop Shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .shimmer()
        )

        Column(modifier = Modifier.padding(16.dp)) {
            // Title Shimmer
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(32.dp).shimmer())
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(20.dp).shimmer())

            Spacer(modifier = Modifier.height(24.dp))

            // Metadata row
            Row {
                Box(modifier = Modifier.size(40.dp, 24.dp).shimmer())
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(80.dp, 24.dp).shimmer())
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(100.dp, 24.dp).shimmer())
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Overview Section
            Box(modifier = Modifier.fillMaxWidth(0.3f).height(24.dp).shimmer())
            Spacer(modifier = Modifier.height(12.dp))
            repeat(4) {
                Box(modifier = Modifier.fillMaxWidth().height(16.dp).shimmer())
                Spacer(modifier = Modifier.height(8.dp))
            }
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).shimmer())

            Spacer(modifier = Modifier.height(32.dp))

            // Cast Section
            Box(modifier = Modifier.fillMaxWidth(0.3f).height(24.dp).shimmer())
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(100.dp).clip(CircleShape).shimmer())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.size(80.dp, 16.dp).shimmer())
                    }
                }
            }
        }
    }
}
