package org.jbtasks

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentTool
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.ProvideStringSubgraphResult
import ai.koog.agents.ext.agent.SubgraphResult
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.cdimascio.dotenv.dotenv
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jbtasks.tools.ReportCreationTools
import org.jbtasks.tools.WebSearchTool


class DeepResearchAgent {

    val executor = simpleOpenAIExecutor(dotenv()["OPENAI_API_KEY"])

    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
        tools(ReportCreationTools().asTools())
        tool(WebSearchTool)
    }

    /*val strategy: AIAgentStrategy<String, String> = strategy("Research Strategy") {

        val nodeCreateReportPlan by nodeLLMRequest("create-report-plan",allowToolCalls = false)
        val nodeGenerateSearchQueries by nodeLLMRequestStructured<List<String>>("generate-search-queries")
        val nodeSearchWeb by nodeExecuteTool("web-search")
        val nodeWriteSection by nodeLLMRequestStructured<List<String>>("write-sections")
        val writeFinalReport by nodeLLMRequest("write-final-report")
        val saveReport by nodeExecuteTool("save-report")

        edge(nodeStart forwardTo nodeCreateReportPlan)
        edge(nodeCreateReportPlan forwardTo nodeGenerateSearchQueries onAssistantMessage {true})
        edge(nodeGenerateSearchQueries forwardTo nodeSearchWeb onToolCall {true})
        edge(nodeSearchWeb forwardTo nodeWriteSection onAssistantMessage {true})
        edge(nodeWriteSection forwardTo nodeSearchWeb onToolCall {true})

        // end
        edge(nodeWriteSection forwardTo writeFinalReport onAssistantMessage {true})
        edge(writeFinalReport forwardTo saveReport onToolCall {true})
        edge(saveReport forwardTo nodeFinish onAssistantMessage { true })
    }*/
    val strategy: AIAgentStrategy<String, String> = strategy("Research Strategy") {

        val nodeCreateReportPlan by node<String, String>("create-report-plan") {
            input ->  llm.writeSession {
                updatePrompt {
                    user("Your task is to create a detailed plan for a research report on the following topic: $input. " +
                            "Break down the report into main sections and subsections, providing a brief description of what each section will cover. " +
                            "Format the plan in markdown with sections as H2 and subsections as H3."
                    )

                }
                val response = requestLLMWithoutTools()
                response.content
            }
        }

        val nodeSearchWeb by subgraphWithTask<String>(
            tools = listOf(WebSearchTool)
            ) {
                    input  -> """
                Your task is to generate a list of specific search queries based on the following report plan: $input. 
                Create queries that will help gather relevant information for each section and subsection of the report.
                Seach the web for these queries, so that in the next step you can write sections based on the search results.
                Provide for each section (also enumerate them for better understanding) the web results you found in the following format:
                # Report Topic: [Your Report Topic]
                ## Section X: Section Title
                Information gathered from websearch for section X, that will be helpful to write this section...
                ## Section Y: Section Title
                Information gathered from websearch for section Y, that will be helpful to write this section...
                ...
                Make sure to cover all sections and subsections from the report plan.
                You can search the web multiple times if needed to gather sufficient information.
                Do not overcrowd the report with too many search results, just provide the most relevant information for each section.
            """.trimIndent()
        }
        val nodeWriteSection by subgraphWithTask<String>(
            tools = ReportCreationTools().asTools()
        ) {
            input -> """
                You are given a report plan based on the following format:
                ## Section 1: Title
                Web search results for section 1...
                ## Section 2: Title
                Web search results for section 2...
                ...
                Based on this plan and the web search results, your task is to write detailed sections for the report in markdown format.
                For each section use the information from the web search results to create comprehensive and well-structured content.
                Ensure that each section is clearly marked with H2 headers and subsections with H3 headers as per the plan.
                You can use the tool to see the current content of the report and append new sections to it.
                Make sure to cover all sections and subsections from the report plan. 
                Make sure you append everything you write to the report using the tool.
                When you write something you will NOT be able to delete anything, you only append to the list,
                hence make sure you write everything in the right order.
                In the end, you should have a complete report based on the initial plan and the gathered information.
                Here is the report plan and web search results to get you started:
                Make sure you write the report in markdown format.
                $input
            """.trimIndent()
        }

        edge(nodeStart forwardTo nodeCreateReportPlan)
        edge(nodeCreateReportPlan forwardTo nodeSearchWeb)
        edge(nodeSearchWeb forwardTo nodeWriteSection onAssistantMessage {true})
        edge(nodeWriteSection forwardTo nodeFinish onAssistantMessage {true} )


    }

    val agent = AIAgent(
        executor = executor,
        llmModel = OpenAIModels.Chat.GPT4o,
        id="deep-research-koog-test",
        strategy = reActStrategy(
            reasoningInterval = 1
        ),
        systemPrompt = "You are a highly intelligent research assistant that helps researchers by gathering, analyzing, summarizing information" +
                "and generating detailed reports on complex topics. You excel at deep research, critical thinking, and presenting information in a clear manner."+
            "You have access to a variety of tools, including web search and report creation tools, to assist you in your tasks."+
            "When creating a report, make sure to search the web about necessary topics and gather relevant information before writing each section of the report." +
                "In the end, you should write a report in markdown format with your given tools.",
        toolRegistry = toolRegistry,
        maxIterations = 100,
        // just to see agent traces detailedly
        installFeatures = {
            install(OpenTelemetry) {
                setSampler(Sampler.alwaysOn())
                setServiceInfo("deep-research-agent", "1.0.0")
                addSpanExporter(
                    OtlpHttpSpanExporter
                        .builder()
                        .setEndpoint("http://localhost:4318/v1/traces")
                        .build()
                )
                setVerbose(true)
            }
        }
    )

    fun research(message: String) = runBlocking {
        agent.run(message)
    }

}