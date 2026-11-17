package com.aster.core.network.model

/** 空选择器表示当前记录节点；attribute 为空时取文本 */
data class HtmlField(val selector: String = "", val attribute: String? = null)
