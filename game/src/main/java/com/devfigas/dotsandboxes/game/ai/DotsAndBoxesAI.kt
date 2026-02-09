package com.devfigas.dotsandboxes.game.ai

import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState

interface DotsAndBoxesAI {
    fun selectMove(state: DotsAndBoxesGameState): DotsAndBoxesMove?
}
