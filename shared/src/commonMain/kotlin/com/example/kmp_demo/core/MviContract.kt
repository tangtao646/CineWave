package com.example.kmp_demo.core

/**
 * MVI 协议基础接口
 */
interface IUiState

interface IUiIntent

interface IUiEffect

/**
 * 带有全屏页面状态的 UI State 接口
 */
interface IBaseState : IUiState {
    val pageStatus: PageStatus
}
