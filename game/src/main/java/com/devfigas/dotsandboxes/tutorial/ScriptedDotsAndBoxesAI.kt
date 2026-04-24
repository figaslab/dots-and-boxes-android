package com.devfigas.dotsandboxes.tutorial

import com.devfigas.dotsandboxes.game.ai.DotsAndBoxesAI
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState

class ScriptedDotsAndBoxesAI(private val lines: List<DotsAndBoxesLine>) : DotsAndBoxesAI {
    override fun selectMove(state: DotsAndBoxesGameState): DotsAndBoxesMove? {
        val aiMoveIndex = state.moveHistory.count { it.player != state.myColor }
        val target = lines.getOrNull(aiMoveIndex) ?: return null
        return DotsAndBoxesMove(target, state.currentTurn)
    }
}
