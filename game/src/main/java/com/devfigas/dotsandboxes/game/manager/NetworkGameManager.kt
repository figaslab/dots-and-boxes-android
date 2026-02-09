package com.devfigas.dotsandboxes.game.manager

import android.os.Handler
import android.os.Looper
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesBoard
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesMove
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesRules
import com.devfigas.dotsandboxes.game.engine.PlayerColor
import com.devfigas.dotsandboxes.game.message.DotsAndBoxesMessageAdapter.decodeMoveData
import com.devfigas.dotsandboxes.game.message.DotsAndBoxesMessageAdapter.encodeMoveData
import com.devfigas.dotsandboxes.game.message.DotsAndBoxesMessageAdapter.toPlayerColor
import com.devfigas.dotsandboxes.game.message.DotsAndBoxesMessageAdapter.toSideName
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGamePhase
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameResult
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState
import com.devfigas.dotsandboxes.game.state.SyncStatus
import com.devfigas.gridgame.model.PlayerSide
import com.devfigas.mockpvp.game.PvpLobbyGameState
import com.devfigas.mockpvp.game.PvpGamePhase
import com.devfigas.mockpvp.game.PvpNetworkGameManager
import com.devfigas.mockpvp.message.PvpMessage
import com.devfigas.mockpvp.message.PvpMessage.Companion.toPayload
import com.devfigas.mockpvp.model.GameMode
import com.devfigas.mockpvp.model.User
import lib.devfigas.P2PKit
import lib.devfigas.model.domain.entity.ConnectionType
import lib.devfigas.model.domain.entity.Status
import java.util.UUID

class NetworkGameManager(
    onStateChanged: (DotsAndBoxesGameState) -> Unit,
    onError: (String) -> Unit,
    private val gameMode: GameMode
) : DotsAndBoxesGameManager(onStateChanged, onError), PvpNetworkGameManager {

    private var lobbyStateCallback: ((PvpLobbyGameState) -> Unit)? = null
    private var lobbyErrorCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "NetworkGameManager"
        private const val RETRY_DELAY_MS = 3000L
        private const val MAX_RETRIES = 3
        private const val CHALLENGE_TIMEOUT_MS = 30_000L
        private const val TURN_TIMEOUT_MS = 30_000L
        private const val OPPONENT_TIMEOUT_MS = 45_000L
    }

    private var isChallenger: Boolean = false
    private var lastSentMove: PvpMessage.Move? = null
    private var isApplyingReceivedMove: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var pendingMoveMessage: PvpMessage.Move? = null
    private var retryCount = 0
    private var retryRunnable: Runnable? = null
    private var challengeTimeoutRunnable: Runnable? = null
    private var turnTimeoutRunnable: Runnable? = null
    private var opponentTimeoutRunnable: Runnable? = null
    private var stateBeforePendingMove: DotsAndBoxesGameState? = null

    override fun challenge(opponent: User) {
        val gameId = UUID.randomUUID().toString().take(8)
        isChallenger = true

        val state = DotsAndBoxesGameState.createNew(gameMode, PlayerColor.RED, opponent).copy(
            gameId = gameId,
            phase = DotsAndBoxesGamePhase.WAITING_CHALLENGE,
            isHost = true,
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(state)
        sendMessage(opponent, PvpMessage.Challenge(gameId))
        startChallengeTimeout(opponent.name)
    }

    override fun challengeWithInitialState(player: User, initialState: String) {
        challenge(player)
    }

    private fun startChallengeTimeout(opponentName: String) {
        cancelChallengeTimeout()
        challengeTimeoutRunnable = Runnable {
            val state = currentState
            if (state?.phase == DotsAndBoxesGamePhase.WAITING_CHALLENGE) {
                notifyError("$opponentName did not respond. They may be offline.")
                resetGame()
            }
        }
        handler.postDelayed(challengeTimeoutRunnable!!, CHALLENGE_TIMEOUT_MS)
    }

    private fun cancelChallengeTimeout() {
        challengeTimeoutRunnable?.let { handler.removeCallbacks(it) }
        challengeTimeoutRunnable = null
    }

    private fun startTurnTimers() {
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return

        if (state.currentTurn == state.myColor) {
            updateState(state.copy(turnStartTime = System.currentTimeMillis()))
            turnTimeoutRunnable = Runnable { handleTurnTimeout() }
            handler.postDelayed(turnTimeoutRunnable!!, TURN_TIMEOUT_MS)
        } else {
            opponentTimeoutRunnable = Runnable { handleOpponentTimeout() }
            handler.postDelayed(opponentTimeoutRunnable!!, OPPONENT_TIMEOUT_MS)
        }
    }

    private fun cancelTurnTimer() {
        turnTimeoutRunnable?.let { handler.removeCallbacks(it) }
        turnTimeoutRunnable = null
    }

    private fun cancelOpponentTimeoutTimer() {
        opponentTimeoutRunnable?.let { handler.removeCallbacks(it) }
        opponentTimeoutRunnable = null
    }

    fun checkAndHandleTimeout() {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return
        if (state.currentTurn == state.myColor) {
            val elapsed = System.currentTimeMillis() - state.turnStartTime
            if (elapsed >= TURN_TIMEOUT_MS) handleTurnTimeout()
        }
    }

    private fun handleTurnTimeout() {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return
        if (state.currentTurn != state.myColor) return

        val opponent = state.opponent ?: return
        sendMessage(opponent, PvpMessage.TimeoutLoss(state.gameId))

        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.TIMEOUT, redScore, blueScore)
        ))
    }

    private fun handleOpponentTimeout() {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return
        if (state.currentTurn == state.myColor) return

        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor, DotsAndBoxesGameResult.Reason.TIMEOUT, redScore, blueScore)
        ))
    }

    override fun resetGame() {
        cancelChallengeTimeout()
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        cancelPendingRetry()
        super.resetGame()
    }

    override fun handleMessage(senderIp: String, senderName: String, senderExtras: String, payload: String) {
        val message = PvpMessage.fromPayload(payload) ?: return
        val parts = senderExtras.split("|", limit = 3)
        val avatar = parts.getOrElse(0) { User.DEFAULT_AVATAR }.ifEmpty { User.DEFAULT_AVATAR }
        val rankId = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val sender = User(name = senderName, ip = senderIp, avatar = avatar, rankId = rankId)

        var state = currentState
        val isFromOpponentByIp = state?.opponent?.ip == senderIp
        val isFromOpponentByName = state?.opponent?.name?.equals(senderName, ignoreCase = true) == true

        if (state?.opponent != null && !isFromOpponentByIp && !isFromOpponentByName && message !is PvpMessage.Challenge) return

        if (state?.opponent != null && isFromOpponentByName && !isFromOpponentByIp) {
            val updatedOpponent = state.opponent!!.copy(ip = senderIp)
            val updatedState = state.copy(opponent = updatedOpponent)
            updateState(updatedState)
            state = updatedState
        }

        when (message) {
            is PvpMessage.Challenge -> handleChallenge(sender, message)
            is PvpMessage.Accept -> handleAccept(sender, message)
            is PvpMessage.Reject -> handleReject(message)
            is PvpMessage.Move -> handleMove(message)
            is PvpMessage.Chat -> handleChat(sender, message)
            is PvpMessage.RematchVote -> handleRematchVote(message)
            is PvpMessage.RematchStart -> handleRematchStart(sender, message)
            is PvpMessage.Resign -> handleResign(message)
            is PvpMessage.Leave -> handleLeave(message)
            is PvpMessage.TimeoutLoss -> handleTimeoutLoss(message)
            is PvpMessage.SyncRequest -> handleSyncRequest(message)
            is PvpMessage.SyncState -> handleSyncState(message)
        }
    }

    private fun handleChallenge(sender: User, message: PvpMessage.Challenge) {
        val currentPhase = currentState?.phase
        val myGameId = currentState?.gameId

        if (currentPhase == DotsAndBoxesGamePhase.WAITING_CHALLENGE && myGameId != null) {
            if (message.gameId < myGameId) {
                cancelChallengeTimeout()
            } else return
        } else if (currentPhase != null && currentPhase != DotsAndBoxesGamePhase.GAME_OVER) return

        isChallenger = false
        val state = DotsAndBoxesGameState.createNew(gameMode, PlayerColor.RED, sender).copy(
            gameId = message.gameId,
            phase = DotsAndBoxesGamePhase.CHALLENGE_RECEIVED,
            isHost = false,
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(state)
    }

    fun acceptChallenge(assignChallengerColor: PlayerColor) {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.CHALLENGE_RECEIVED) return
        val opponent = state.opponent ?: return

        val myColor = assignChallengerColor.opposite()
        val newState = DotsAndBoxesGameState.createForChallenge(state.gameId, gameMode, myColor, opponent).copy(
            isHost = false, isUnlimitedTime = true, timerActive = false
        )
        updateState(newState)
        sendMessage(opponent, PvpMessage.Accept(state.gameId, assignChallengerColor.toSideName()))
        startTurnTimers()
    }

    override fun rejectChallenge() {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.CHALLENGE_RECEIVED) return
        val opponent = state.opponent ?: return
        sendMessage(opponent, PvpMessage.Reject(state.gameId))
        resetGame()
    }

    private fun handleAccept(sender: User, message: PvpMessage.Accept) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        if (state.phase != DotsAndBoxesGamePhase.WAITING_CHALLENGE) return
        cancelChallengeTimeout()

        val challengerColor = message.challengerSide.toPlayerColor()
        val newState = DotsAndBoxesGameState.createForChallenge(state.gameId, gameMode, challengerColor, sender).copy(
            isHost = true, isUnlimitedTime = true, timerActive = false
        )
        updateState(newState)
        startTurnTimers()
    }

    private fun handleReject(message: PvpMessage.Reject) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        cancelChallengeTimeout()
        notifyError("Challenge rejected by ${state.opponent?.name}")
        resetGame()
    }

    override fun startGame(myColor: PlayerColor, opponent: User?) {
        if (opponent == null) return
        val gameId = UUID.randomUUID().toString().take(8)
        val state = DotsAndBoxesGameState.createNew(gameMode, myColor, opponent).copy(
            gameId = gameId, phase = DotsAndBoxesGamePhase.PLAYING,
            isUnlimitedTime = true, timerActive = false
        )
        updateState(state)
    }

    override fun selectLine(line: com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine) {
        val state = currentState ?: return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return
        if (state.currentTurn != state.myColor) return
        super.selectLine(line)
    }

    override fun applyMove(move: DotsAndBoxesMove) {
        if (!isApplyingReceivedMove) {
            stateBeforePendingMove = currentState
        }
        super.applyMove(move)
    }

    override fun onMoveApplied(move: DotsAndBoxesMove, newState: DotsAndBoxesGameState, extraTurn: Boolean) {
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        if (isApplyingReceivedMove) {
            stateBeforePendingMove = null
            startTurnTimers()
            return
        }

        val opponent = newState.opponent ?: return
        val newMoveNum = newState.moveNum + 1

        val stateWithMoveNum = newState.copy(moveNum = newMoveNum, syncStatus = SyncStatus.WAITING_CONFIRMATION)
        updateState(stateWithMoveNum)

        val moveMessage = PvpMessage.Move(
            gameId = newState.gameId,
            moveData = encodeMoveData(move.line),
            moveNum = newMoveNum
        )
        sendMoveWithRetry(opponent, moveMessage)
        startTurnTimers()
    }

    private fun handleMove(message: PvpMessage.Move) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        if (state.phase != DotsAndBoxesGamePhase.PLAYING) return

        lastSentMove?.let { sent ->
            if (sent.moveData == message.moveData && sent.gameId == message.gameId) {
                lastSentMove = null
                return
            }
        }

        cancelPendingRetry()
        if (state.syncStatus == SyncStatus.WAITING_CONFIRMATION) {
            updateState(state.copy(syncStatus = SyncStatus.SYNCED))
        }

        val expectedMoveNum = state.moveNum + 1
        if (message.moveNum != expectedMoveNum) {
            if (message.moveNum <= state.moveNum) return
            if (!state.isHost) requestSync()
            return
        }

        if (state.currentTurn == state.myColor) return

        val line = decodeMoveData(message.moveData) ?: return
        if (state.board.isLineDrawn(line)) return

        val move = DotsAndBoxesMove(line, state.currentTurn)

        isApplyingReceivedMove = true
        try {
            applyMove(move)
            currentState?.let { newState ->
                updateState(newState.copy(moveNum = message.moveNum))
            }
        } finally {
            isApplyingReceivedMove = false
        }
    }

    private fun handleChat(sender: User, message: PvpMessage.Chat) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        updateState(state.copy(incomingChatMessage = message.text))
    }

    override fun sendChatMessage(message: String) {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        sendMessage(opponent, PvpMessage.Chat(state.gameId, message))
    }

    override fun resign() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        sendMessage(opponent, PvpMessage.Resign(state.gameId))

        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.RESIGNATION, redScore, blueScore)
        ))
    }

    override fun leave() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        sendMessage(opponent, PvpMessage.Leave(state.gameId))

        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor.opposite(), DotsAndBoxesGameResult.Reason.OPPONENT_LEFT, redScore, blueScore)
        ))
    }

    private fun handleResign(message: PvpMessage.Resign) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor, DotsAndBoxesGameResult.Reason.RESIGNATION, redScore, blueScore)
        ))
    }

    private fun handleLeave(message: PvpMessage.Leave) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor, DotsAndBoxesGameResult.Reason.OPPONENT_LEFT, redScore, blueScore)
        ))
    }

    private fun handleTimeoutLoss(message: PvpMessage.TimeoutLoss) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        val (redScore, blueScore) = DotsAndBoxesRules.getScore(state.board)
        updateState(state.copy(
            phase = DotsAndBoxesGamePhase.GAME_OVER,
            result = DotsAndBoxesGameResult(state.myColor, DotsAndBoxesGameResult.Reason.TIMEOUT, redScore, blueScore)
        ))
    }

    override fun voteRematch(vote: Boolean) {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        sendMessage(opponent, PvpMessage.RematchVote(state.gameId, vote))
        if (vote) {
            updateState(state.copy(myRematchVote = vote, phase = DotsAndBoxesGamePhase.WAITING_REMATCH))
        } else {
            updateState(state.copy(myRematchVote = vote))
        }
        checkRematchVotes()
    }

    fun cancelRematch() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        sendMessage(opponent, PvpMessage.RematchVote(state.gameId, false))
        updateState(state.copy(myRematchVote = false, phase = DotsAndBoxesGamePhase.GAME_OVER))
    }

    private fun handleRematchVote(message: PvpMessage.RematchVote) {
        val state = currentState ?: return
        updateState(state.copy(opponentRematchVote = message.vote))
        checkRematchVotes()
    }

    override fun checkRematchVotes() {
        val state = currentState ?: return
        if (state.myRematchVote == true && state.opponentRematchVote == true) {
            if (state.isHost) restartGame()
        }
    }

    private fun handleRematchStart(sender: User, message: PvpMessage.RematchStart) {
        val opponentColor = message.opponentSide.toPlayerColor()
        val newState = DotsAndBoxesGameState.createForChallenge(message.newGameId, gameMode, opponentColor, sender).copy(
            isHost = false, isUnlimitedTime = true, timerActive = false
        )
        updateState(newState)
    }

    override fun restartGame() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        val newColor = state.myColor.opposite()
        val newGameId = UUID.randomUUID().toString().take(8)
        val opponentColor = newColor.opposite()

        sendMessage(opponent, PvpMessage.RematchStart(
            oldGameId = state.gameId, newGameId = newGameId, opponentSide = opponentColor.toSideName()
        ))

        val newState = DotsAndBoxesGameState.createForChallenge(newGameId, gameMode, newColor, opponent).copy(
            isHost = true, isUnlimitedTime = true, timerActive = false
        )
        updateState(newState)
    }

    private fun sendMoveWithRetry(recipient: User, message: PvpMessage.Move) {
        lastSentMove = message
        pendingMoveMessage = message
        retryCount = 0
        doSendMove(recipient, message)
    }

    private fun doSendMove(recipient: User, message: PvpMessage.Move) {
        val connectionType = when (gameMode) {
            GameMode.BLUETOOTH -> ConnectionType.BLUETOOTH
            else -> ConnectionType.WIFI
        }
        val peer = recipient.toPeer(connectionType)
        P2PKit.messenger.sendMessage(
            receipt = peer, content = message.toPayload(),
            onPrepareMessage = { },
            onSendMessage = { msg ->
                if (msg.deliveryStatus == Status.FAILURE) scheduleRetry(recipient, message)
            }
        )
    }

    private fun scheduleRetry(recipient: User, message: PvpMessage.Move) {
        if (retryCount >= MAX_RETRIES) {
            stateBeforePendingMove?.let { previousState ->
                updateState(previousState.copy(syncStatus = SyncStatus.SYNCED))
                stateBeforePendingMove = null
            }
            pendingMoveMessage = null
            notifyError("Move delivery failed. Please try again.")
            return
        }
        retryCount++
        retryRunnable = Runnable {
            if (pendingMoveMessage == message) doSendMove(recipient, message)
        }
        handler.postDelayed(retryRunnable!!, RETRY_DELAY_MS)
    }

    private fun cancelPendingRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        pendingMoveMessage = null
        retryCount = 0
        stateBeforePendingMove = null
    }

    private fun requestSync() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return
        updateState(state.copy(syncStatus = SyncStatus.SYNCING))
        sendMessage(opponent, PvpMessage.SyncRequest(state.gameId, state.moveNum))
    }

    private fun handleSyncRequest(message: PvpMessage.SyncRequest) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        if (!state.isHost) return
        val opponent = state.opponent ?: return

        val boardData = state.board.encode()
        sendMessage(opponent, PvpMessage.SyncState(
            gameId = state.gameId, stateData = boardData,
            currentSide = state.currentTurn.toSideName(), moveNum = state.moveNum
        ))
    }

    private fun handleSyncState(message: PvpMessage.SyncState) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        if (state.isHost) return

        val newBoard = DotsAndBoxesBoard.decode(message.stateData) ?: run {
            notifyError("Sync failed - invalid board state")
            return
        }

        val nextTurn = message.currentSide.toPlayerColor()
        val newState = state.copy(
            board = newBoard, currentTurn = nextTurn,
            moveNum = message.moveNum, syncStatus = SyncStatus.SYNCED
        )
        updateState(newState)
    }

    private fun sendMessage(recipient: User, message: PvpMessage) {
        val connectionType = when (gameMode) {
            GameMode.BLUETOOTH -> ConnectionType.BLUETOOTH
            else -> ConnectionType.WIFI
        }
        val peer = recipient.toPeer(connectionType)
        P2PKit.messenger.sendMessage(
            receipt = peer, content = message.toPayload(),
            onPrepareMessage = { },
            onSendMessage = { msg ->
                if (msg.deliveryStatus == Status.FAILURE) notifyError("Failed to send message")
            }
        )
    }

    // --- PvpNetworkGameManager interface ---

    override fun acceptChallenge(challengerSide: PlayerSide) {
        val color = challengerSide.toPlayerColor()
        acceptChallenge(color)
    }

    override fun getLobbyState(): PvpLobbyGameState? {
        val state = currentState ?: return null
        return state.toPvpLobbyState()
    }

    override fun updateLobbyCallbacks(
        onStateChanged: (PvpLobbyGameState) -> Unit,
        onError: (String) -> Unit
    ) {
        lobbyStateCallback = onStateChanged
        lobbyErrorCallback = onError
        updateCallbacks(
            onStateChanged = { gameState -> onStateChanged(gameState.toPvpLobbyState()) },
            onError = onError
        )
    }

    private fun DotsAndBoxesGameState.toPvpLobbyState(): PvpLobbyGameState {
        return PvpLobbyGameState(
            phase = phase.toPvpPhase(), opponent = opponent,
            mySide = myColor.toPlayerSide(), gameId = gameId
        )
    }

    private fun DotsAndBoxesGamePhase.toPvpPhase(): PvpGamePhase {
        return when (this) {
            DotsAndBoxesGamePhase.WAITING_CHALLENGE -> PvpGamePhase.WAITING_CHALLENGE
            DotsAndBoxesGamePhase.CHALLENGE_RECEIVED -> PvpGamePhase.CHALLENGE_RECEIVED
            DotsAndBoxesGamePhase.PLAYING -> PvpGamePhase.PLAYING
            DotsAndBoxesGamePhase.GAME_OVER -> PvpGamePhase.GAME_OVER
            DotsAndBoxesGamePhase.WAITING_REMATCH -> PvpGamePhase.WAITING_REMATCH
        }
    }

    private fun PlayerColor.toPlayerSide(): PlayerSide {
        return when (this) {
            PlayerColor.RED -> PlayerSide.FIRST
            PlayerColor.BLUE -> PlayerSide.SECOND
        }
    }

    private fun PlayerSide.toPlayerColor(): PlayerColor {
        return when (this) {
            PlayerSide.FIRST -> PlayerColor.RED
            PlayerSide.SECOND -> PlayerColor.BLUE
        }
    }
}
