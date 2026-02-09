package com.devfigas.reversi.game.ai

import com.devfigas.reversi.game.engine.ReversiMove
import com.devfigas.reversi.game.state.ReversiGameState

interface ReversiAI {
    fun selectMove(state: ReversiGameState): ReversiMove?
}
