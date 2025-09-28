package org.jbtasks

import kotlinx.coroutines.runBlocking
import org.jbtasks.tools.ReportCreationTools
import org.jbtasks.tools.WebSearchTools

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    val agent = DeepResearchAgent()

    runBlocking { agent.research("Create a detailed report explaining the theory of relativity.") }

}
