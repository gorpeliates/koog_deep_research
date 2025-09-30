package org.jbtasks

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.ProvideStringSubgraphResult
import ai.koog.agents.ext.agent.ProvideVerifiedSubgraphResult
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import org.jbtasks.strategies.deepResearchStrategy
import org.jbtasks.tools.ReportCreationTools
import org.jbtasks.tools.WebSearchTool


class DeepResearchAgent {

    val executor = simpleOpenAIExecutor(dotenv()["OPENAI_API_KEY"])

    val toolRegistry = ToolRegistry {
        tool(SayToUser)
        tool(ExitTool)
        tools(ReportCreationTools().asTools())
        tool(WebSearchTool)
        tool(ProvideStringSubgraphResult)
        tool(ProvideVerifiedSubgraphResult)
    }

    val agent = AIAgent(
        executor = executor,
        llmModel = OpenAIModels.Chat.GPT5,
        id="deep-research-koog-test",
        strategy = deepResearchStrategy(toolRegistry=toolRegistry),
        systemPrompt = "You are an research assistant that excels at creating detailed reports in a structured manner," +
                "given a research topic. Your tasks are as follows:" +
                "1. Create a detailed plan for the report, breaking it down into main sections and subsections." +
                "2. Search the web for relevant information that you need to write the report." +
                "3. Use your tools to create a report in MARKDOWN format based on the information you have gathered." +
                "You can use your tools to see the current content of the report and add sections to it." +
                "In the end, check the content of the report then finish your task." +
                "DO NOT ask the user for any information to clarify, just use the tools you have to complete your tasks." ,
        toolRegistry = toolRegistry
    )

    fun research(message: String) = runBlocking {
        agent.run(message)
    }

}