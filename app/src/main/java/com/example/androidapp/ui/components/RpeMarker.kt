package com.example.androidapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.androidapp.R
import com.example.androidapp.domain.model.Rpe

/**
 * `RPE 9.5` — the one place a stored half-point count becomes what a lifter reads
 * (ROADMAP N6, B6).
 *
 * Three screens show an RPE and all three read the same marker string. Wrapping
 * [Rpe.format] here rather than at each call site is the difference between a value
 * that reads 9.5 everywhere and one that reads 19 wherever a call site was missed —
 * which is exactly what B6 was.
 */
@Composable
fun rpeMarker(halves: Int): String = stringResource(R.string.set_rpe_marker, Rpe.format(halves))
