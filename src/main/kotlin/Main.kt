package org.jbtasks

import kotlinx.coroutines.runBlocking
import org.jbtasks.tools.WebSearchTool

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    val agent = DeepResearchAgent()

    runBlocking { agent.research("Create a detailed report explaining the theory of relativity.") }
}
