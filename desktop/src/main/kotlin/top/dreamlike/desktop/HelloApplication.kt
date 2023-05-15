package top.dreamlike.desktop

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.Stage


class HelloApplication : Application() {
    override fun start(stage: Stage) {
        val javaVersion = System.getProperty("java.version")
        val javafxVersion = System.getProperty("javafx.version")
        val l =
            Label("Hello, JavaFX $javafxVersion, running on Java $javaVersion. ${Thread.currentThread()}")
        val scene = Scene(StackPane(l), 640.0, 480.0)
        stage.scene = scene
        StageInfo(stage)
        stage.show()
    }
}

fun main() {
    Application.launch(HelloApplication::class.java)
}

class StageInfo(stage: Stage) {
    var x = stage.x
    val y = stage.y

    init {
        stage.xProperty().addListener { observable, oldValue, newValue ->
            x = newValue.toDouble()
            println("newValue:$x")
        }
    }
}