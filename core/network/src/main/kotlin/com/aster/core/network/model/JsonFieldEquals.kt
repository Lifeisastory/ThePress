package com.aster.core.network.model

import com.aster.core.network.adapters.mapping.JsonPath
import kotlinx.serialization.json.JsonPrimitive

data class JsonFieldEquals(val path: JsonPath, val value: JsonPrimitive)
