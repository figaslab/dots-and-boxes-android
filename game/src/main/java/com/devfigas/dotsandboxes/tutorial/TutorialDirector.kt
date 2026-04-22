package com.devfigas.dotsandboxes.tutorial

import android.app.Activity
import android.graphics.RectF
import com.devfigas.dotsandboxes.R
import com.devfigas.dotsandboxes.game.engine.DotsAndBoxesLine
import com.devfigas.dotsandboxes.game.engine.LineOrientation
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGamePhase
import com.devfigas.dotsandboxes.game.state.DotsAndBoxesGameState
import com.devfigas.dotsandboxes.ui.DotsAndBoxesBoardView
import ui.devfigas.uikit.tutorial.TutorialOverlayView

/**
 * Dots and Boxes tutorial. Short two-move introduction that teaches basic
 * line drawing and shows how boxes start filling in.
 */
class TutorialDirector(
    private val activity: Activity,
    private val overlay: TutorialOverlayView,
    private val boardView: DotsAndBoxesBoardView,
    private val playLine: (DotsAndBoxesLine) -> Unit,
    private val onFinished: () -> Unit
) {
    companion object {
        val AI_SCRIPT: List<DotsAndBoxesLine> = listOf(
            DotsAndBoxesLine(LineOrientation.HORIZONTAL, 4, 3)
        )
        val PLAYER_ALLOWED_LINES: List<DotsAndBoxesLine> = listOf(
            DotsAndBoxesLine(LineOrientation.HORIZONTAL, 0, 0),
            DotsAndBoxesLine(LineOrientation.VERTICAL, 0, 0)
        )
    }

    private enum class Advance { BUTTON, MOVE }

    private data class Step(
        val messageRes: Int,
        val buttonRes: Int,
        val advance: Advance,
        val targetLine: DotsAndBoxesLine? = null,
        val balloonSide: TutorialOverlayView.BalloonSide = TutorialOverlayView.BalloonSide.BOTTOM
    )

    private val steps: List<Step> = listOf(
        Step(R.string.tutorial_welcome, R.string.tutorial_next, Advance.BUTTON),
        Step(R.string.tutorial_objective, R.string.tutorial_next, Advance.BUTTON),
        Step(R.string.tutorial_first_move, R.string.tutorial_next, Advance.MOVE,
            targetLine = PLAYER_ALLOWED_LINES[0], balloonSide = TutorialOverlayView.BalloonSide.BOTTOM),
        Step(R.string.tutorial_second_move, R.string.tutorial_next, Advance.MOVE,
            targetLine = PLAYER_ALLOWED_LINES[1], balloonSide = TutorialOverlayView.BalloonSide.BOTTOM),
        Step(R.string.tutorial_congrats, R.string.tutorial_play, Advance.BUTTON)
    )

    private var currentStepIndex = 0
    private var lastObservedMoveCount = -1
    private var finished = false

    fun start() {
        currentStepIndex = 0
        showCurrentStep()
    }

    fun onGameStateChanged(state: DotsAndBoxesGameState) {
        if (finished) return
        val moveCount = state.moveHistory.size

        if (state.phase == DotsAndBoxesGamePhase.GAME_OVER) {
            currentStepIndex = steps.size - 1
            showCurrentStep()
            return
        }

        if (moveCount != lastObservedMoveCount) {
            lastObservedMoveCount = moveCount
            val step = currentStep()
            if (step?.advance == Advance.MOVE && moveCount > 0 && state.currentTurn == state.myColor) {
                advance()
                return
            }
        }

        if (state.currentTurn == state.myColor && currentStep()?.advance == Advance.MOVE) {
            showCurrentStep()
        } else if (state.currentTurn != state.myColor && state.phase == DotsAndBoxesGamePhase.PLAYING) {
            overlay.hide()
        }
    }

    fun allowedLines(): Set<DotsAndBoxesLine>? {
        val step = currentStep() ?: return emptySet()
        if (step.advance != Advance.MOVE) return emptySet()
        val l = step.targetLine ?: return null
        return setOf(l)
    }

    fun onSkipRequested() {
        finish()
    }

    private fun currentStep(): Step? = steps.getOrNull(currentStepIndex)

    private fun advance() {
        currentStepIndex++
        if (currentStepIndex >= steps.size) {
            finish()
            return
        }
        showCurrentStep()
    }

    private fun showCurrentStep() {
        val step = currentStep() ?: return
        val message = activity.getString(step.messageRes)
        val buttonLabel = if (step.advance == Advance.BUTTON) activity.getString(step.buttonRes) else null
        val isLast = currentStepIndex == steps.size - 1
        val skipLabel = if (isLast) null else activity.getString(R.string.tutorial_skip_tutorial)

        val targetRect = step.targetLine?.let { boardView.getLineRectInWindow(it) }
            ?.let { translateToOverlay(it) }

        boardView.post {
            overlay.showStep(
                target = targetRect,
                message = message,
                buttonText = buttonLabel,
                skipButtonText = skipLabel,
                showArrow = false,
                balloonSide = step.balloonSide,
                blockOutsideTouches = step.advance == Advance.BUTTON,
                onAdvance = {
                    if (step.advance == Advance.BUTTON) {
                        if (isLast) finish() else advance()
                    }
                },
                onSkip = if (isLast) null else ({ finish() })
            )
        }
    }

    private fun translateToOverlay(windowRect: RectF): RectF {
        val overlayLoc = IntArray(2)
        overlay.getLocationInWindow(overlayLoc)
        return RectF(
            windowRect.left - overlayLoc[0],
            windowRect.top - overlayLoc[1],
            windowRect.right - overlayLoc[0],
            windowRect.bottom - overlayLoc[1]
        )
    }

    private fun finish() {
        if (finished) return
        finished = true
        overlay.hide()
        TutorialPreferences.markCompleted(activity)
        onFinished()
    }
}
