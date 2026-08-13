package com.primaloptima.scribe.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Phase 2: centralises hazeSource so individual screens don't each need
 * `.hazeSource(hazeState)`. Nav3 applies this decorator to every non-overlay
 * scene automatically; OverlayScene (dialogs) is intentionally skipped by Nav3.
 *
 * Key override is required: without it the derived key never changes and
 * Nav3's built-in animations stop working. Pattern from official Nav3 docs.
 */
class HazeSourceDecoratorStrategy<T : Any>(
    private val hazeState: HazeState
) : SceneDecoratorStrategy<T> {

    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> =
        HazeDecoratedScene(scene, hazeState)
}

private data class HazeDecoratedScene<T : Any>(
    val scene: Scene<T>,
    val hazeState: HazeState
) : Scene<T> by scene {

    // Must differ from inner scene's key so Nav3 animation keys stay unique.
    override val key = scene::class to scene.key

    override val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            scene.content()
        }
    }
}
