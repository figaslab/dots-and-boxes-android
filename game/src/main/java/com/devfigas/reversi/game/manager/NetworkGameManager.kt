package com.devfigas.reversi.game.manager

import android.os.Handler
import android.os.Looper
import com.devfigas.reversi.game.engine.Position
import com.devfigas.reversi.game.engine.ReversiBoard
import com.devfigas.reversi.game.engine.ReversiColor
import com.devfigas.reversi.game.engine.ReversiMove
import com.devfigas.reversi.game.engine.ReversiPiece
import com.devfigas.reversi.game.engine.ReversiRules
import com.devfigas.reversi.game.message.ReversiMessageAdapter.decodeMoveData
import com.devfigas.reversi.game.message.ReversiMessageAdapter.encodeMoveData
import com.devfigas.reversi.game.message.ReversiMessageAdapter.encodePass
import com.devfigas.reversi.game.message.ReversiMessageAdapter.toReversiColor
import com.devfigas.reversi.game.message.ReversiMessageAdapter.toSideName
import com.devfigas.reversi.game.state.ReversiGamePhase
import com.devfigas.reversi.game.state.ReversiGameResult
import com.devfigas.reversi.game.state.ReversiGameState
import com.devfigas.reversi.game.state.SyncStatus
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

/**
 * Manages Reversi game state and P2P communication for Bluetooth/WiFi multiplayer
 */
class NetworkGameManager(
    onStateChanged: (ReversiGameState) -> Unit,
    onError: (String) -> Unit,
    private val gameMode: GameMode
) : ReversiGameManager(onStateChanged, onError), PvpNetworkGameManager {

    // Lobby-level callbacks (wraps reversi state -> PvpLobbyGameState)
    private var lobbyStateCallback: ((PvpLobbyGameState) -> Unit)? = null
    private var lobbyErrorCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "NetworkGameManager"
        private const val RETRY_DELAY_MS = 3000L
        private const val MAX_RETRIES = 3
        private const val CHALLENGE_TIMEOUT_MS = 30_000L  // 30 seconds to respond to challenge
        private const val TURN_TIMEOUT_MS = 30_000L       // 30 seconds per turn (self-timeout = I lose)
        private const val OPPONENT_TIMEOUT_MS = 45_000L   // 45 seconds grace period for opponent timeout (I win)
    }

    private var isChallenger: Boolean = false
    private var lastSentMove: PvpMessage.Move? = null
    private var isApplyingReceivedMove: Boolean = false

    // Retry mechanism
    private val handler = Handler(Looper.getMainLooper())
    private var pendingMoveMessage: PvpMessage.Move? = null
    private var retryCount = 0
    private var retryRunnable: Runnable? = null

    // Challenge timeout
    private var challengeTimeoutRunnable: Runnable? = null

    // Turn timeout (self - I lose after 30s)
    private var turnTimeoutRunnable: Runnable? = null

    // Opponent timeout (fallback - I win if opponent doesn't play in 45s)
    private var opponentTimeoutRunnable: Runnable? = null

    // State before pending move - used for undo on failure
    private var stateBeforePendingMove: ReversiGameState? = null

    /**
     * Start a new challenge against an opponent
     */
    override fun challenge(opponent: User) {
        android.util.Log.d(TAG, "challenge: opponent=${opponent.name}")
        val gameId = UUID.randomUUID().toString().take(8)
        isChallenger = true

        val baseState = ReversiGameState.createNew(gameMode, ReversiColor.BLACK, opponent).copy(
            gameId = gameId,
            phase = ReversiGamePhase.WAITING_CHALLENGE,
            isHost = true
        )

        // WiFi/Bluetooth P2P games are ALWAYS unlimited (no timer due to latency)
        val state = baseState.copy(
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(state)

        android.util.Log.d(TAG, "Sending Challenge message")
        sendMessage(opponent, PvpMessage.Challenge(gameId))

        // Start challenge timeout
        startChallengeTimeout(opponent.name)
    }

    override fun challengeWithInitialState(player: User, initialState: String) {
        // Reversi doesn't support custom initial states, fall back to standard challenge
        challenge(player)
    }

    /**
     * Start a timeout for waiting on challenge response
     */
    private fun startChallengeTimeout(opponentName: String) {
        cancelChallengeTimeout()
        challengeTimeoutRunnable = Runnable {
            val state = currentState
            if (state?.phase == ReversiGamePhase.WAITING_CHALLENGE) {
                android.util.Log.d(TAG, "Challenge timeout - no response from $opponentName")
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

    /**
     * Start the appropriate timer based on whose turn it is.
     * - My turn: 30s timer, if expires I lose
     * - Opponent's turn: 45s timer (fallback), if expires I win
     */
    private fun startTurnTimers() {
        android.util.Log.d(TAG, "startTurnTimers() called")
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        val state = currentState
        if (state == null) {
            android.util.Log.w(TAG, "startTurnTimers: currentState is null!")
            return
        }
        if (state.phase != ReversiGamePhase.PLAYING) {
            android.util.Log.w(TAG, "startTurnTimers: not in PLAYING phase (phase=${state.phase})")
            return
        }

        android.util.Log.d(TAG, "startTurnTimers: myColor=${state.myColor}, currentTurn=${state.currentTurn}")

        if (state.currentTurn == state.myColor) {
            // My turn - start self-timeout timer (30s)
            android.util.Log.d(TAG, "My turn - starting self-timeout timer (${TURN_TIMEOUT_MS}ms)")
            updateState(state.copy(turnStartTime = System.currentTimeMillis()))

            turnTimeoutRunnable = Runnable {
                android.util.Log.d(TAG, "TURN TIMEOUT RUNNABLE FIRED!")
                handleTurnTimeout()
            }
            handler.postDelayed(turnTimeoutRunnable!!, TURN_TIMEOUT_MS)
        } else {
            // Opponent's turn - start fallback timer (45s)
            android.util.Log.d(TAG, "Opponent's turn - starting fallback timer (${OPPONENT_TIMEOUT_MS}ms)")

            opponentTimeoutRunnable = Runnable {
                android.util.Log.d(TAG, "OPPONENT TIMEOUT RUNNABLE FIRED!")
                handleOpponentTimeout()
            }
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

    /**
     * Public method to check and handle timeout - called by Activity as a fallback
     */
    fun checkAndHandleTimeout() {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return

        if (state.currentTurn == state.myColor) {
            val elapsed = System.currentTimeMillis() - state.turnStartTime
            if (elapsed >= TURN_TIMEOUT_MS) {
                android.util.Log.d(TAG, "checkAndHandleTimeout: Turn expired, calling handleTurnTimeout()")
                handleTurnTimeout()
            }
        }
    }

    /**
     * Handle turn timeout - I lose because I didn't play in time
     */
    private fun handleTurnTimeout() {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return

        android.util.Log.d(TAG, "Turn timeout - I lose. myColor=${state.myColor}, currentTurn=${state.currentTurn}")

        // Safety check: only trigger if it's actually my turn
        if (state.currentTurn != state.myColor) {
            android.util.Log.w(TAG, "Turn timeout triggered but it's not my turn! Ignoring.")
            return
        }

        // Notify opponent that I lost by timeout
        val opponent = state.opponent ?: return
        android.util.Log.d(TAG, "Sending TimeoutLoss to ${opponent.name}")
        sendMessage(opponent, PvpMessage.TimeoutLoss(state.gameId))

        // Update local state - opponent wins by timeout
        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor.opposite(), ReversiGameResult.Reason.TIMEOUT, blackScore, whiteScore)
        ))
    }

    /**
     * Handle opponent timeout - opponent didn't play in time (fallback)
     */
    private fun handleOpponentTimeout() {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return

        android.util.Log.d(TAG, "Opponent timeout check. myColor=${state.myColor}, currentTurn=${state.currentTurn}")

        if (state.currentTurn == state.myColor) {
            android.util.Log.w(TAG, "Opponent timeout triggered but it's MY turn! Ignoring.")
            return
        }

        android.util.Log.d(TAG, "Opponent timeout (fallback) - I win")

        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor, ReversiGameResult.Reason.TIMEOUT, blackScore, whiteScore)
        ))
    }

    override fun resetGame() {
        cancelChallengeTimeout()
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()
        cancelPendingRetry()
        super.resetGame()
    }

    /**
     * Handle incoming game message
     */
    override fun handleMessage(senderIp: String, senderName: String, senderExtras: String, payload: String) {
        val message = PvpMessage.fromPayload(payload) ?: return
        // Parse extras format: "avatar|status|rankId"
        val parts = senderExtras.split("|", limit = 3)
        val avatar = parts.getOrElse(0) { User.DEFAULT_AVATAR }.ifEmpty { User.DEFAULT_AVATAR }
        val rankId = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val sender = User(name = senderName, ip = senderIp, avatar = avatar, rankId = rankId)

        var state = currentState

        // BLE addresses can change, so identify opponent by name and update IP if changed
        val isFromOpponentByIp = state?.opponent?.ip == senderIp
        val isFromOpponentByName = state?.opponent?.name?.equals(senderName, ignoreCase = true) == true

        if (state?.opponent != null && !isFromOpponentByIp && !isFromOpponentByName && message !is PvpMessage.Challenge) {
            android.util.Log.w(TAG, "Ignoring message from unknown sender: $senderName ($senderIp)")
            return
        }

        // If the opponent's IP changed, update it
        if (state?.opponent != null && isFromOpponentByName && !isFromOpponentByIp) {
            android.util.Log.d(TAG, "Opponent IP changed from ${state.opponent?.ip} to $senderIp - updating")
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

        // Handle race condition: both players sent challenges at the same time
        if (currentPhase == ReversiGamePhase.WAITING_CHALLENGE && myGameId != null) {
            android.util.Log.d(TAG, "handleChallenge: Race condition detected! myGameId=$myGameId, theirGameId=${message.gameId}")

            if (message.gameId < myGameId) {
                android.util.Log.d(TAG, "handleChallenge: Their gameId wins, we become acceptor")
                cancelChallengeTimeout()
            } else {
                android.util.Log.d(TAG, "handleChallenge: Our gameId wins, ignoring their challenge")
                return
            }
        } else if (currentPhase != null && currentPhase != ReversiGamePhase.GAME_OVER) {
            android.util.Log.d(TAG, "handleChallenge: Ignoring, already in phase $currentPhase")
            return
        }

        isChallenger = false

        val baseState = ReversiGameState.createNew(gameMode, ReversiColor.BLACK, sender).copy(
            gameId = message.gameId,
            phase = ReversiGamePhase.CHALLENGE_RECEIVED,
            isHost = false
        )

        val state = baseState.copy(
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(state)
    }

    /**
     * Accept a received challenge and assign colors
     */
    fun acceptChallenge(assignChallengerColor: ReversiColor) {
        android.util.Log.d(TAG, "acceptChallenge: assignChallengerColor=$assignChallengerColor")
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.CHALLENGE_RECEIVED) return

        val opponent = state.opponent ?: return

        // We get the opposite color
        val myColor = assignChallengerColor.opposite()
        android.util.Log.d(TAG, "acceptChallenge: I get myColor=$myColor (challenger gets $assignChallengerColor)")

        val newState = ReversiGameState.createForChallenge(state.gameId, gameMode, myColor, opponent).copy(
            isHost = false,
            isUnlimitedTime = true,
            timerActive = false
        )
        android.util.Log.d(TAG, "acceptChallenge: newState myColor=${newState.myColor}, currentTurn=${newState.currentTurn}, phase=${newState.phase}")
        updateState(newState)

        sendMessage(opponent, PvpMessage.Accept(state.gameId, assignChallengerColor.toSideName()))

        // Start turn timer
        android.util.Log.d(TAG, "acceptChallenge: calling startTurnTimers()")
        startTurnTimers()
    }

    override fun rejectChallenge() {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.CHALLENGE_RECEIVED) return

        val opponent = state.opponent ?: return
        sendMessage(opponent, PvpMessage.Reject(state.gameId))
        resetGame()
    }

    private fun handleAccept(sender: User, message: PvpMessage.Accept) {
        android.util.Log.d(TAG, "handleAccept: gameId=${message.gameId}, challengerSide=${message.challengerSide}")
        val state = currentState ?: return
        if (state.gameId != message.gameId) return
        if (state.phase != ReversiGamePhase.WAITING_CHALLENGE) return

        cancelChallengeTimeout()

        val challengerColor = message.challengerSide.toReversiColor()

        val newState = ReversiGameState.createForChallenge(state.gameId, gameMode, challengerColor, sender).copy(
            isHost = true,
            isUnlimitedTime = true,
            timerActive = false
        )
        android.util.Log.d(TAG, "handleAccept: newState myColor=${newState.myColor}, currentTurn=${newState.currentTurn}, phase=${newState.phase}")
        updateState(newState)

        // Start turn timer
        android.util.Log.d(TAG, "handleAccept: calling startTurnTimers()")
        startTurnTimers()
    }

    private fun handleReject(message: PvpMessage.Reject) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return

        cancelChallengeTimeout()

        notifyError("Challenge rejected by ${state.opponent?.name}")
        resetGame()
    }

    override fun startGame(myColor: ReversiColor, opponent: User?) {
        // For network mode, startGame is called after challenge is accepted
        if (opponent == null) return

        val gameId = UUID.randomUUID().toString().take(8)
        val baseState = ReversiGameState.createNew(gameMode, myColor, opponent).copy(
            gameId = gameId,
            phase = ReversiGamePhase.PLAYING
        )

        val state = baseState.copy(
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(state)
    }

    override fun selectSquare(position: Position) {
        val state = currentState ?: return
        if (state.phase != ReversiGamePhase.PLAYING) return

        // Only allow moves on our turn
        if (state.currentTurn != state.myColor) {
            return
        }

        super.selectSquare(position)
    }

    override fun applyMove(move: ReversiMove) {
        // Save state before applying for potential undo (only for local moves)
        if (!isApplyingReceivedMove) {
            stateBeforePendingMove = currentState
        }
        super.applyMove(move)
    }

    override fun onMoveApplied(move: ReversiMove, newState: ReversiGameState) {
        android.util.Log.d(TAG, "onMoveApplied: position=${move.position}, isApplyingReceivedMove=$isApplyingReceivedMove")

        // Cancel all timers - turn changed
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        // Only send move to opponent if this was a local move, not a received one
        if (isApplyingReceivedMove) {
            android.util.Log.d(TAG, "onMoveApplied: skipping send (received move)")
            stateBeforePendingMove = null
            // Start appropriate timer (now it's my turn after receiving opponent's move)
            startTurnTimers()
            return
        }

        val opponent = newState.opponent
        if (opponent == null) {
            android.util.Log.e(TAG, "onMoveApplied: no opponent set, cannot send move!")
            return
        }
        android.util.Log.d(TAG, "onMoveApplied: sending move to ${opponent.name} at ${opponent.ip}")

        // Increment moveNum for our move
        val newMoveNum = newState.moveNum + 1

        // Update state with new moveNum and waiting status
        val stateWithMoveNum = newState.copy(
            moveNum = newMoveNum,
            syncStatus = SyncStatus.WAITING_CONFIRMATION
        )
        updateState(stateWithMoveNum)

        val moveMessage = PvpMessage.Move(
            gameId = newState.gameId,
            moveData = encodeMoveData(move.position.row, move.position.col),
            moveNum = newMoveNum
        )

        sendMoveWithRetry(opponent, moveMessage)

        // Now it's opponent's turn - start timer
        startTurnTimers()
    }

    private fun handleMove(message: PvpMessage.Move) {
        android.util.Log.d(TAG, "handleMove: received moveData=${message.moveData}, moveNum=${message.moveNum}")

        val state = currentState ?: return
        if (state.gameId != message.gameId) {
            android.util.Log.w(TAG, "handleMove: gameId mismatch, ignoring")
            return
        }
        if (state.phase != ReversiGamePhase.PLAYING) {
            android.util.Log.w(TAG, "handleMove: not in PLAYING phase, ignoring")
            return
        }

        // Ignore our own move echoed back
        lastSentMove?.let { sent ->
            if (sent.moveData == message.moveData && sent.gameId == message.gameId) {
                lastSentMove = null
                return
            }
        }

        // Receiving opponent's move acts as implicit ACK for our previous move
        cancelPendingRetry()
        if (state.syncStatus == SyncStatus.WAITING_CONFIRMATION) {
            updateState(state.copy(syncStatus = SyncStatus.SYNCED))
        }

        // Check moveNum for sync
        val expectedMoveNum = state.moveNum + 1
        if (message.moveNum != expectedMoveNum) {
            android.util.Log.w(TAG, "Move number mismatch: expected $expectedMoveNum, got ${message.moveNum}")
            if (message.moveNum <= state.moveNum) {
                return
            }
            if (!state.isHost) {
                requestSync()
            }
            return
        }

        // It should be opponent's turn
        if (state.currentTurn == state.myColor) {
            return
        }

        // Handle PASS
        val decoded = decodeMoveData(message.moveData)
        if (decoded == null) {
            // PASS - this is handled automatically by the base manager's applyMove logic
            // In practice, passes are auto-handled, but if we receive an explicit PASS,
            // we just need to update the moveNum
            android.util.Log.d(TAG, "handleMove: received PASS")
            currentState?.let { newState ->
                updateState(newState.copy(moveNum = message.moveNum))
            }
            return
        }

        val (row, col) = decoded
        val position = Position(row, col)

        // Find the move in valid moves
        val validMoves = ReversiRules.getValidMoves(state.board, state.currentTurn)
        val move = validMoves.find { it.position == position }

        if (move == null) {
            android.util.Log.w(TAG, "handleMove: illegal move received at position ($row, $col)")
            notifyError("Illegal move received")
            return
        }

        // Mark that we're applying a received move so we don't send it back
        isApplyingReceivedMove = true
        try {
            applyMove(move)
            // Update moveNum after successful application
            currentState?.let { newState ->
                updateState(newState.copy(moveNum = message.moveNum))
            }
        } finally {
            isApplyingReceivedMove = false
        }
    }

    private fun handleChat(sender: User, message: PvpMessage.Chat) {
        android.util.Log.d(TAG, "handleChat called: ${message.text}")
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

        // Opponent wins
        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor.opposite(), ReversiGameResult.Reason.RESIGNATION, blackScore, whiteScore)
        ))
    }

    override fun leave() {
        val state = currentState ?: run {
            android.util.Log.w(TAG, "leave() called but currentState is null")
            return
        }
        val opponent = state.opponent ?: run {
            android.util.Log.w(TAG, "leave() called but opponent is null")
            return
        }

        android.util.Log.d(TAG, "leave() - sending LEAVE message to ${opponent.name}, gameId=${state.gameId}")

        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        sendMessage(opponent, PvpMessage.Leave(state.gameId))

        android.util.Log.d(TAG, "leave() - LEAVE message sent")

        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor.opposite(), ReversiGameResult.Reason.OPPONENT_LEFT, blackScore, whiteScore)
        ))
    }

    private fun handleResign(message: PvpMessage.Resign) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return

        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        // We win by resignation
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor, ReversiGameResult.Reason.RESIGNATION, blackScore, whiteScore)
        ))
    }

    private fun handleLeave(message: PvpMessage.Leave) {
        android.util.Log.d(TAG, "handleLeave() received - gameId=${message.gameId}")
        val state = currentState ?: return
        if (state.gameId != message.gameId) return

        android.util.Log.d(TAG, "handleLeave() - opponent left, I win!")
        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor, ReversiGameResult.Reason.OPPONENT_LEFT, blackScore, whiteScore)
        ))
    }

    private fun handleTimeoutLoss(message: PvpMessage.TimeoutLoss) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return

        cancelTurnTimer()
        cancelOpponentTimeoutTimer()

        android.util.Log.d(TAG, "Opponent timeout - I win")

        val (blackScore, whiteScore) = ReversiRules.getScore(state.board)
        updateState(state.copy(
            phase = ReversiGamePhase.GAME_OVER,
            result = ReversiGameResult(state.myColor, ReversiGameResult.Reason.TIMEOUT, blackScore, whiteScore)
        ))
    }

    override fun voteRematch(vote: Boolean) {
        val state = currentState ?: return
        val opponent = state.opponent ?: return

        sendMessage(opponent, PvpMessage.RematchVote(state.gameId, vote))

        if (vote) {
            updateState(state.copy(
                myRematchVote = vote,
                phase = ReversiGamePhase.WAITING_REMATCH
            ))
            checkRematchVotes()
        } else {
            updateState(state.copy(myRematchVote = vote))
            checkRematchVotes()
        }
    }

    fun cancelRematch() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return

        sendMessage(opponent, PvpMessage.RematchVote(state.gameId, false))
        updateState(state.copy(
            myRematchVote = false,
            phase = ReversiGamePhase.GAME_OVER
        ))
    }

    private fun handleRematchVote(message: PvpMessage.RematchVote) {
        val state = currentState ?: return

        val isRematchPhase = state.phase == ReversiGamePhase.GAME_OVER || state.phase == ReversiGamePhase.WAITING_REMATCH
        if (state.gameId != message.gameId && !isRematchPhase) {
            android.util.Log.d(TAG, "handleRematchVote: Ignoring vote for different game")
            return
        }

        android.util.Log.d(TAG, "handleRematchVote: vote=${message.vote}, phase=${state.phase}, myVote=${state.myRematchVote}")

        updateState(state.copy(opponentRematchVote = message.vote))
        checkRematchVotes()
    }

    override fun checkRematchVotes() {
        val state = currentState ?: return
        if (state.myRematchVote == true && state.opponentRematchVote == true) {
            if (state.isHost) {
                android.util.Log.d(TAG, "checkRematchVotes: I'm host, initiating rematch restart")
                restartGame()
            } else {
                android.util.Log.d(TAG, "checkRematchVotes: Waiting for host to initiate rematch")
            }
        } else if (state.myRematchVote == false || state.opponentRematchVote == false) {
            android.util.Log.d(TAG, "checkRematchVotes: Someone voted no, rematch cancelled")
        }
    }

    private fun handleRematchStart(sender: User, message: PvpMessage.RematchStart) {
        val state = currentState ?: return

        val opponentColor = message.opponentSide.toReversiColor()
        android.util.Log.d(TAG, "handleRematchStart: newGameId=${message.newGameId}, myColor=$opponentColor")

        val newState = ReversiGameState.createForChallenge(message.newGameId, gameMode, opponentColor, sender).copy(
            isHost = false,
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(newState)
    }

    override fun restartGame() {
        val state = currentState ?: return
        val opponent = state.opponent ?: return

        val newColor = state.myColor.opposite()
        val newGameId = UUID.randomUUID().toString().take(8)
        val opponentColor = newColor.opposite()

        android.util.Log.d(TAG, "restartGame: newGameId=$newGameId, myNewColor=$newColor, opponentColor=$opponentColor")

        // Send RematchStart to opponent
        sendMessage(opponent, PvpMessage.RematchStart(
            oldGameId = state.gameId,
            newGameId = newGameId,
            opponentSide = opponentColor.toSideName()
        ))

        val newState = ReversiGameState.createForChallenge(newGameId, gameMode, newColor, opponent).copy(
            isHost = true,
            isUnlimitedTime = true,
            timerActive = false
        )
        updateState(newState)
    }

    /**
     * Send a move with retry mechanism
     */
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
            receipt = peer,
            content = message.toPayload(),
            onPrepareMessage = { /* Message prepared */ },
            onSendMessage = { msg ->
                if (msg.deliveryStatus == Status.FAILURE) {
                    scheduleRetry(recipient, message)
                }
            }
        )
    }

    private fun scheduleRetry(recipient: User, message: PvpMessage.Move) {
        if (retryCount >= MAX_RETRIES) {
            android.util.Log.d(TAG, "Max retries reached, undoing move ${message.moveData}")

            stateBeforePendingMove?.let { previousState ->
                updateState(previousState.copy(
                    syncStatus = SyncStatus.SYNCED
                ))
                stateBeforePendingMove = null
                android.util.Log.d(TAG, "Move undone, restored previous state")
            }

            pendingMoveMessage = null
            notifyError("Move delivery failed. Please try again.")
            return
        }

        retryCount++
        android.util.Log.d(TAG, "Scheduling retry $retryCount for move ${message.moveData}")

        retryRunnable = Runnable {
            if (pendingMoveMessage == message) {
                android.util.Log.d(TAG, "Retrying move ${message.moveData}")
                doSendMove(recipient, message)
            }
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

        android.util.Log.d(TAG, "Requesting sync from host, my moveNum: ${state.moveNum}")

        updateState(state.copy(syncStatus = SyncStatus.SYNCING))
        sendMessage(opponent, PvpMessage.SyncRequest(state.gameId, state.moveNum))
    }

    /**
     * Handle sync request from non-host player (only host responds)
     */
    private fun handleSyncRequest(message: PvpMessage.SyncRequest) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return

        if (!state.isHost) {
            android.util.Log.d(TAG, "Ignoring sync request - not host")
            return
        }

        android.util.Log.d(TAG, "Received sync request, opponent moveNum: ${message.myMoveNum}, my moveNum: ${state.moveNum}")

        val opponent = state.opponent ?: return

        // Encode board state as 64-char string + "|turn|moveNum"
        val boardData = encodeBoardState(state.board)
        sendMessage(opponent, PvpMessage.SyncState(
            gameId = state.gameId,
            stateData = boardData,
            currentSide = state.currentTurn.toSideName(),
            moveNum = state.moveNum
        ))
    }

    /**
     * Handle sync state from host
     */
    private fun handleSyncState(message: PvpMessage.SyncState) {
        val state = currentState ?: return
        if (state.gameId != message.gameId) return

        if (state.isHost) {
            android.util.Log.d(TAG, "Ignoring sync state - we are host")
            return
        }

        android.util.Log.d(TAG, "Received sync state, applying board data")

        val newBoard = decodeBoardState(message.stateData)
        if (newBoard == null) {
            android.util.Log.e(TAG, "Failed to parse board from sync state")
            notifyError("Sync failed - invalid board state")
            return
        }

        val nextTurn = message.currentSide.toReversiColor()
        val nextValidMoves = ReversiRules.getValidMoves(newBoard, nextTurn).map { it.position }

        val newState = state.copy(
            board = newBoard,
            currentTurn = nextTurn,
            moveNum = message.moveNum,
            validMoves = nextValidMoves,
            syncStatus = SyncStatus.SYNCED
        )

        updateState(newState)
        android.util.Log.d(TAG, "Sync complete, now at moveNum: ${message.moveNum}")
    }

    /**
     * Encode board as 64-char string where each char is 'B' (black), 'W' (white), or '.' (empty)
     */
    private fun encodeBoardState(board: ReversiBoard): String {
        val sb = StringBuilder(64)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val piece = board.getPiece(row, col)
                sb.append(when {
                    piece == null -> '.'
                    piece.color == ReversiColor.BLACK -> 'B'
                    piece.color == ReversiColor.WHITE -> 'W'
                    else -> '.'
                })
            }
        }
        return sb.toString()
    }

    /**
     * Decode 64-char board string back into a ReversiBoard
     */
    private fun decodeBoardState(data: String): ReversiBoard? {
        if (data.length < 64) return null

        val board = ReversiBoard.createEmpty()
        for (i in 0 until 64) {
            val row = i / 8
            val col = i % 8
            when (data[i]) {
                'B' -> board.setPiece(row, col, ReversiPiece(ReversiColor.BLACK))
                'W' -> board.setPiece(row, col, ReversiPiece(ReversiColor.WHITE))
                // '.' -> empty, no action needed
            }
        }
        return board
    }

    private fun sendMessage(recipient: User, message: PvpMessage) {
        val connectionType = when (gameMode) {
            GameMode.BLUETOOTH -> ConnectionType.BLUETOOTH
            else -> ConnectionType.WIFI
        }
        val peer = recipient.toPeer(connectionType)
        P2PKit.messenger.sendMessage(
            receipt = peer,
            content = message.toPayload(),
            onPrepareMessage = { /* Message prepared */ },
            onSendMessage = { msg ->
                if (msg.deliveryStatus == Status.FAILURE) {
                    notifyError("Failed to send message")
                }
            }
        )
    }

    // --- PvpNetworkGameManager interface implementation ---

    override fun acceptChallenge(challengerSide: PlayerSide) {
        val color = challengerSide.toReversiColor()
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
            onStateChanged = { reversiState ->
                onStateChanged(reversiState.toPvpLobbyState())
            },
            onError = onError
        )
    }

    // --- Conversion helpers ---

    private fun ReversiGameState.toPvpLobbyState(): PvpLobbyGameState {
        return PvpLobbyGameState(
            phase = phase.toPvpPhase(),
            opponent = opponent,
            mySide = myColor.toPlayerSide(),
            gameId = gameId
        )
    }

    private fun ReversiGamePhase.toPvpPhase(): PvpGamePhase {
        return when (this) {
            ReversiGamePhase.WAITING_CHALLENGE -> PvpGamePhase.WAITING_CHALLENGE
            ReversiGamePhase.CHALLENGE_RECEIVED -> PvpGamePhase.CHALLENGE_RECEIVED
            ReversiGamePhase.PLAYING -> PvpGamePhase.PLAYING
            ReversiGamePhase.GAME_OVER -> PvpGamePhase.GAME_OVER
            ReversiGamePhase.WAITING_REMATCH -> PvpGamePhase.WAITING_REMATCH
        }
    }

    private fun ReversiColor.toPlayerSide(): PlayerSide {
        return when (this) {
            ReversiColor.BLACK -> PlayerSide.FIRST
            ReversiColor.WHITE -> PlayerSide.SECOND
        }
    }

    private fun PlayerSide.toReversiColor(): ReversiColor {
        return when (this) {
            PlayerSide.FIRST -> ReversiColor.BLACK
            PlayerSide.SECOND -> ReversiColor.WHITE
        }
    }
}
