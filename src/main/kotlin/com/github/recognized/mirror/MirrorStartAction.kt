package com.github.recognized.mirror

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.jetbrains.rd.util.firstOrNull
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import mirror.Mirror
import mirror.MirrorServer
import mirror.MirrorServer.EnableCompressionInterceptor
import mirror.tasks.ThreadBasedTaskFactory
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class MirrorStartAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project != null) {
            val server = project.getUserData(stateKey)
            ApplicationManager.getApplication().invokeLater {
                SettingsDialog(project, server).show()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        with(presentation) {
            description = "Edit mirror settings"
            isEnabled = project != null
            isVisible = project != null
            if (project != null) {
                val state = project.getUserData(stateKey)
                when (val s = state?.getCurrentState()) {
                    is Disabled -> {
                    }
                    is Synced -> {
                    }
                    is InProgress -> {
                        text = s.message
                    }
                }
            }
        }
    }
}

val stateKey = Key.create<MirrorServerWrapper>("com.github.recognized.mirror.MirrorServerState")

class MirrorServerWrapper(val server: MirrorServer,
                          val host: String = defaultHost,
                          val port: Int = defaultPort) : Disposable {
    private var rpc: Server? = null

    fun getCurrentState(): MirrorServerState {
        val session = server.sessions.values.firstOrNull() ?: return Disabled
        val incomingUpdates = session.queues.incomingQueue.size
        val outgoingUpdates = session.queues.saveToRemote.size
        return if (incomingUpdates > 0 || outgoingUpdates > 0) {
            InProgress("$incomingUpdates incoming / $outgoingUpdates outgoing")
        } else {
            Synced
        }
    }

    @Synchronized
    override fun dispose() {
        server.sessions.forEach {
            it.value.stop()
        }
        rpc?.shutdown()
    }

    @Synchronized
    fun run() {
        rpc = NettyServerBuilder
                .forAddress(InetSocketAddress(host, port))
                .maxInboundMessageSize(maxMessageSize)
                .keepAliveTime(keepAliveInSeconds.toLong(), TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeoutInSeconds.toLong(), TimeUnit.SECONDS) // add in /2 to whatever the client is sending to account for latency
                .permitKeepAliveTime((keepAliveInSeconds / 2).toLong(), TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(true)
                .intercept(EnableCompressionInterceptor())
                .addService(server)
                .build()
        rpc?.start()
    }

    val projectKey: String? get() {
        return server.mounts?.firstOrNull()?.key
    }

    companion object {
        private const val maxMessageSize = 1073741824 // 1gb
        private const val defaultPort = 49172
        private const val defaultHost = "0.0.0.0"
        private const val keepAliveInSeconds = 20
        private const val keepAliveTimeoutInSeconds = 5
    }
}

sealed class MirrorServerState
object Disabled : MirrorServerState()
object Synced : MirrorServerState()
class InProgress(val message: String) : MirrorServerState()
