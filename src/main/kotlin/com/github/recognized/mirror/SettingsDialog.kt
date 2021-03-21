package com.github.recognized.mirror

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import mirror.MirrorServer
import mirror.NativeFileAccessFactory
import mirror.tasks.ThreadBasedTaskFactory
import org.jdesktop.swingx.action.AbstractActionExt
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import com.intellij.notification.NotificationDisplayType

import com.intellij.notification.NotificationGroup


private val NOTIFICATION_GROUP = NotificationGroup("Mirror Server Notifications", NotificationDisplayType.BALLOON, true)

class SettingsDialog(
        private val project: Project,
        private val activeMirror: MirrorServerWrapper?
) : DialogWrapper(project, true) {
    private val field by lazy { JBTextField(activeMirror?.projectKey ?: project.name) }

    init {
        init()
        title = "Mirror Settings"
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel(VerticalFlowLayout())
        val label = JLabel("Project key:")
        dialogPanel.add(label, VerticalFlowLayout.LEFT)
        dialogPanel.add(field)
        label.preferredSize = Dimension(300, 100)
        return dialogPanel
    }

    override fun doValidate(): ValidationInfo? {
        if (!field.text.matches("[a-zA-Z\\-_0-9]+".toRegex())) {
            return ValidationInfo("Project key is invalid")
        }
        return null
    }

    override fun getOKAction(): Action {
        val superAction = super.getOKAction()
        return object : Action by superAction {
            override fun actionPerformed(e: ActionEvent?) {
                Disposer.dispose(currentDisposable)
                currentDisposable = Disposer.newDisposable()
                val wrapper = MirrorServerWrapper(MirrorServer(
                        ThreadBasedTaskFactory(),
                        NativeFileAccessFactory(),
                        { _, incomingQueue ->
                            IdeaProjectFileWatcher(project, currentDisposable, incomingQueue)
                        },
                        mapOf(field.text to project.basePath)
                ))
                Disposer.register(currentDisposable, wrapper)
                wrapper.run()
                project.putUserData(stateKey, wrapper)
                NOTIFICATION_GROUP.createNotification(
                        "Mirror started",
                        "Project is now running with key ${field.text}",
                        NotificationType.INFORMATION
                ).notify(project)
                superAction.actionPerformed(e)
            }
        }
    }

    override fun isOKActionEnabled(): Boolean {
        return field.text != activeMirror?.projectKey
    }

    companion object {
        private var currentDisposable = Disposer.newDisposable()
    }
}