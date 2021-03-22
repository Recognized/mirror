package com.github.recognized.mirror

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import mirror.MirrorServer
import mirror.NativeFileAccessFactory
import mirror.tasks.ThreadBasedTaskFactory
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


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
        val dialogPanel = JPanel()
        dialogPanel.layout = BoxLayout(dialogPanel, BoxLayout.Y_AXIS)
        val label = JBLabel("Project key:")
        label.alignmentX = Component.LEFT_ALIGNMENT
        field.alignmentX = Component.LEFT_ALIGNMENT
        isOKActionEnabled = activeMirror == null
        field.document.addDocumentListener(object : DocumentListener {
            fun update() {
                isOKActionEnabled = field.text != activeMirror?.projectKey
            }

            override fun insertUpdate(e: DocumentEvent?) = update()
            override fun removeUpdate(e: DocumentEvent?) = update()
            override fun changedUpdate(e: DocumentEvent?) = update()
        })
        val strut = Box.createHorizontalStrut(JBUI.pixScale(240f).toInt())
        (strut as Box.Filler).alignmentX = Component.LEFT_ALIGNMENT
        dialogPanel.add(label)
        dialogPanel.add(field)
        dialogPanel.add(strut)
        return dialogPanel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return field
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

    companion object {
        private var currentDisposable = Disposer.newDisposable()
    }
}