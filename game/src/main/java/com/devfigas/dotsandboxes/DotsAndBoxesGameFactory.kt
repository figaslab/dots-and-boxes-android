package com.devfigas.dotsandboxes

import android.app.Activity
import android.content.Context
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.dotsandboxes.game.manager.NetworkGameManager
import com.devfigas.gridgame.model.PlayerSide
import com.devfigas.mockpvp.PvpGameFactory
import com.devfigas.mockpvp.game.PvpLobbyGameState
import com.devfigas.mockpvp.game.PvpNetworkGameManager
import com.devfigas.mockpvp.model.GameMode

class DotsAndBoxesGameFactory : PvpGameFactory {
    override fun getGameActivityClass(): Class<out Activity> = DotsAndBoxesGameActivity::class.java
    override fun getAvailableGameModes(): List<GameMode> = listOf(GameMode.CPU, GameMode.LOCAL, GameMode.BLUETOOTH, GameMode.WIFI, GameMode.INTERNET)
    override fun getGameDisplayName(): String = "Dots and Boxes"
    override fun getGameSubtitle(context: Context): String = context.getString(R.string.app_name)
    override fun getChallengeText(context: Context): String = context.getString(R.string.wants_to_play)
    override fun getSideLabels(context: Context): Pair<String, String> = Pair(context.getString(R.string.assign_red), context.getString(R.string.assign_blue))
    override fun getAdAppKey(): String? = BuildConfig.APPODEAL_APP_KEY
    override fun isDebugBuild(): Boolean = BuildConfig.DEBUG
    override fun getVersionName(): String = BuildConfig.VERSION_NAME
    override fun getDebugOpponentSideSetting(context: Context): String = "RANDOM"
    override fun getDebugInitialState(context: Context): String? = null
    override fun setupDebugButton(activity: Activity) { }
    override fun getExtraMyColorKey(): String = DotsAndBoxesGameActivity.EXTRA_MY_COLOR
    override fun getExtraOpponentKey(): String = DotsAndBoxesGameActivity.EXTRA_OPPONENT
    override fun getExtraGameIdKey(): String = DotsAndBoxesGameActivity.EXTRA_GAME_ID
    override fun getSideValueForIntent(side: PlayerSide): String = if (side == PlayerSide.FIRST) PlayerColor.RED.name else PlayerColor.BLUE.name
    override fun createNetworkGameManager(onStateChanged: (PvpLobbyGameState) -> Unit, onError: (String) -> Unit, gameMode: GameMode): PvpNetworkGameManager {
        val manager = NetworkGameManager(onStateChanged = { }, onError = onError, gameMode = gameMode)
        manager.updateLobbyCallbacks(onStateChanged, onError)
        return manager
    }
    override fun getDebugAutoResponse(): String = "ACCEPT"
    override fun getDebugRematchAutoResponse(): String = "ACCEPT"
}
