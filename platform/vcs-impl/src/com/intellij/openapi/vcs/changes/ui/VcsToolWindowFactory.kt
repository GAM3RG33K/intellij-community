// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.CONTENT_PROVIDER_SUPPLIER_KEY
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowIdFor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil.getClientProperty
import com.intellij.util.ui.UIUtil.putClientProperty
import javax.swing.JPanel

private val Project.changesViewContentManager: ChangesViewContentI
  get() = ChangesViewContentManager.getInstance(this)

private val IS_CONTENT_CREATED = Key.create<Boolean>("ToolWindow.IsContentCreated")
private val CHANGES_VIEW_EXTENSION = Key.create<ChangesViewContentEP>("Content.ChangesViewExtension")

abstract class VcsToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(window: ToolWindow) {
    val project = (window as ToolWindowEx).project

    updateState(project, window)
    with(project.messageBus.connect()) {
      subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
        runInEdt {
          if (project.isDisposed) return@runInEdt
          updateState(project, window)
        }
      })
      subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
        override fun toolWindowMappingChanged() = updateState(project, window)
      })
    }
  }

  override fun shouldBeAvailable(project: Project): Boolean = project.vcsManager.hasAnyMappings()

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    updateContent(project, toolWindow)
    project.changesViewContentManager.attachToolWindow(toolWindow)

    putClientProperty(toolWindow.component, IS_CONTENT_CREATED, true)
  }

  protected open fun updateState(project: Project, toolWindow: ToolWindow) {
    updateAvailability(project, toolWindow)
    if (getClientProperty(toolWindow.component, IS_CONTENT_CREATED) == true) {
      updateContent(project, toolWindow)
    }
  }

  private fun updateAvailability(project: Project, toolWindow: ToolWindow) {
    val available = shouldBeAvailable(project)

    // force showing stripe button on adding initial mapping even if stripe button was manually removed by the user
    if (available && !toolWindow.isAvailable) {
      toolWindow.isShowStripeButton = true
    }

    toolWindow.setAvailable(available)
  }

  private fun updateContent(project: Project, toolWindow: ToolWindow) {
    val changesViewContentManager = project.changesViewContentManager

    getExtensions(project, toolWindow).forEach { extension ->
      val isVisible = extension.newPredicateInstance(project)?.`fun`(project) != false
      val content = changesViewContentManager.findContents { it.getUserData(CHANGES_VIEW_EXTENSION) === extension }.firstOrNull()

      if (isVisible && content == null) {
        changesViewContentManager.addContent(createExtensionContent(project, extension))
      }
      else if (!isVisible && content != null) {
        changesViewContentManager.removeContent(content)
      }
    }
  }

  private fun getExtensions(project: Project, toolWindow: ToolWindow): Collection<ChangesViewContentEP> =
    ChangesViewContentEP.EP_NAME.getExtensions(project).filter { getToolWindowIdFor(project, it.tabName) == toolWindow.id }

  private fun createExtensionContent(project: Project, extension: ChangesViewContentEP): Content =
    ContentFactory.SERVICE.getInstance().createContent(JPanel(null), extension.getTabName(), false).apply {
      isCloseable = false
      putUserData(CHANGES_VIEW_EXTENSION, extension)
      putUserData(CONTENT_PROVIDER_SUPPLIER_KEY) { extension.getInstance(project) }

      extension.newPreloaderInstance(project)?.preloadTabContent(this)
    }

  companion object {
    internal val Project.vcsManager: ProjectLevelVcsManager get() = ProjectLevelVcsManager.getInstance(this)
  }
}