package com.xuzheng.tiyuengine.data

/** Rejects excessive nesting before the recursive platform JSON parser allocates objects. */
internal fun requireBoundedJson(json: String, maxDepth: Int = 32) {
    var depth = 0
    var quoted = false
    var escaped = false
    var previous: Char? = null
    var closedString = false
    json.forEach { character ->
        if (quoted) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> {
                    quoted = false
                    closedString = true
                    previous = character
                }
            }
        } else if (!character.isWhitespace()) {
            if (closedString) {
                require(character in ":,]}") { "JSON 文本格式无效" }
                closedString = false
            }
            when (character) {
                '"' -> {
                    require(previous == null || previous in "{[:,") { "JSON 文本格式无效" }
                    quoted = true
                }
                '{', '[' -> {
                    depth++
                    require(depth <= maxDepth) { "JSON 结构嵌套过深" }
                }
                '}', ']' -> {
                    depth--
                    require(depth >= 0) { "JSON 结构无效" }
                }
                else -> require(character in ":,0123456789.-+eEtruefalsn") { "JSON 文本格式无效" }
            }
            previous = character
        }
    }
    require(!quoted && depth == 0) { "JSON 结构不完整" }
}
