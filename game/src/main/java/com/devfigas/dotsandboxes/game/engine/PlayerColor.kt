package com.devfigas.dotsandboxes.game.engine

enum class PlayerColor {
    RED,
    BLUE;

    fun opposite(): PlayerColor = if (this == RED) BLUE else RED
}
