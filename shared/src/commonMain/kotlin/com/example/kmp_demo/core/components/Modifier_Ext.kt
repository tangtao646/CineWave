package com.example.kmp_demo.core.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier


fun Modifier.safeContent() = this
    .statusBarsPadding()
    .navigationBarsPadding()
