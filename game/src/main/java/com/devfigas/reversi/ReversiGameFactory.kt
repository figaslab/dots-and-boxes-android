package com.devfigas.reversi

import android.app.Activity
import android.content.Context
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.manager.NetworkGameManager
import com.devfigas.gridgame.model.PlayerSide
import com.devfigas.mockpvp.PvpGameFactory
import com.devfigas.mockpvp.game.PvpLobbyGameState
import com.devfigas.mockpvp.game.PvpNetworkGameManager
import com.devfigas.mockpvp.model.GameMode

class ReversiGameFactory : PvpGameFactory {
    override fun getGameActivityClass(): Class<out Activity> = ReversiGameActivity::class.java
    override fun getAvailableGameModes(): List<GameMode> = listOf(GameMode.CPU, GameMode.LOCAL, GameMode.BLUETOOTH, GameMode.WIFI, GameMode.INTERNET)
    override fun getGameDisplayName(): String = "Reversi"
    override fun getGameSubtitle(context: Context): String = context.getString(R.string.app_name)
    override fun getChallengeText(context: Context): String = context.getString(R.string.wants_to_play_reversi)
    override fun getSideLabels(context: Context): Pair<String, String> = Pair(context.getString(R.string.assign_black), context.getString(R.string.assign_white))
    override fun getAdAppKey(): String? = BuildConfig.APPODEAL_APP_KEY
    override fun isDebugBuild(): Boolean = BuildConfig.DEBUG
    override fun getVersionName(): String = BuildConfig.VERSION_NAME
    override fun getDebugOpponentSideSetting(context: Context): String = "RANDOM"
    override fun getDebugInitialState(context: Context): String? = null
    override fun setupDebugButton(activity: Activity) { /* No debug button for now */ }
    override fun getExtraMyColorKey(): String = ReversiGameActivity.EXTRA_MY_COLOR
    override fun getExtraOpponentKey(): String = ReversiGameActivity.EXTRA_OPPONENT
    override fun getExtraGameIdKey(): String = ReversiGameActivity.EXTRA_GAME_ID
    override fun getSideValueForIntent(side: PlayerSide): String = if (side == PlayerSide.FIRST) ReversiColor.BLACK.name else ReversiColor.WHITE.name
    override fun createNetworkGameManager(onStateChanged: (PvpLobbyGameState) -> Unit, onError: (String) -> Unit, gameMode: GameMode): PvpNetworkGameManager {
        val manager = NetworkGameManager(onStateChanged = { }, onError = onError, gameMode = gameMode)
        manager.updateLobbyCallbacks(onStateChanged, onError)
        return manager
    }
    override fun getDebugAutoResponse(): String = "ACCEPT"
    override fun getDebugRematchAutoResponse(): String = "ACCEPT"
}
