package com.example.androidapp.ui.home

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.ui.programs.StartIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The home screen's writes that are more than a call (ROADMAP P3.11).
 *
 * File-level rather than members of the route's file, which is at the length and function count
 * this project allows, and because neither reads any composition state: they build a callback for
 * the screen and take their sinks as parameters.
 */

/**
 * Records a substitute for one occurrence and starts it (ROADMAP P3.11).
 *
 * The pick is made at the point of starting, so the write and the start are one action — and
 * clearing writes nothing to start.
 */
internal fun substituteOccurrence(
    scope: CoroutineScope,
    viewModel: WorkoutsHomeViewModel,
    requestStart: (StartIntent) -> Unit,
    onFailure: (DataError) -> Unit,
): (TodayPlan, String?) -> Unit = { plan, templateId ->
    val slotId = plan.slotId
    if (slotId != null) {
        scope.launch {
            when (val result = viewModel.setSubstitution(slotId, templateId)) {
                is DataResult.Success -> if (templateId != null) {
                    requestStart(
                        StartIntent(
                            templateId = templateId,
                            // The slot is what it was scheduled as, so its prescription still
                            // seeds what it can (P3.8, P3.11).
                            slotId = slotId,
                            label = plan.name,
                        ),
                    )
                }

                is DataResult.Failure -> onFailure(result.error)
            }
        }
    }
}
