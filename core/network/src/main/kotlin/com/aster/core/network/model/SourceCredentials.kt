package com.aster.core.network.model

/** 由宿主注入；不在源码、日志或资源文件中保存凭据 */
class SourceCredentials(val productHuntToken: String? = null, val weiboCookie: String? = null)
