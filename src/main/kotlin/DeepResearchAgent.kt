package org.jbtasks

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeUpdatePrompt
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import kotlinx.coroutines.runBlocking
import org.jbtasks.tools.ReportCreationTools
import org.jbtasks.tools.WebSearchTools


class DeepResearchAgent {

    val executor = simpleOpenAIExecutor(dotenv()["OPENAI_API_KEY"])

    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
        tools(ReportCreationTools().asTools())
        tools(WebSearchTools().asTools())
    }

    val strategy: AIAgentStrategy<String, String> = strategy("Research Strategy") {

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
    }

    val agent = AIAgent(
        executor = executor,
        llmModel = OpenAIModels.Chat.GPT5Mini,
        id="deep-research-koog-test",
        strategy = strategy,
        systemPrompt = "You are a highly intelligent research assistant that helps researchers by gathering, analyzing, summarizing information" +
                "and generating detailed reports on complex topics. You excel at deep research, critical thinking, and presenting information in a clear manner."+
            "You have access to a variety of tools, including web search and report creation tools, to assist you in your tasks."+
            "When creating a report, make sure to search the web about necessary topics and gather relevant information before writing each section of the report." +
                "In the end, you should write a report in markdown format with your given tools.",
        toolRegistry = toolRegistry,
        maxIterations = 100,
        // just to see agent traces in a detailed manner
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