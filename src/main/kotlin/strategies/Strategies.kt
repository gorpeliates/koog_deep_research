package org.jbtasks.strategies

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.prompt.message.Message
import org.jbtasks.tools.ReportCreationTools


fun deepResearchStrategy(
    name: String = "DeepResearchStrategy",
    toolRegistry : ToolRegistry
): AIAgentStrategy<String, String> = strategy(name) {

    val reportPlanStorage = createStorageKey<String>("report_plan")

    val nodeCreateReportPlan by node<String, String>("create-report-plan") {
            input ->  llm.writeSession {
        updatePrompt {
            user("Your task is to create a detailed plan for a research report on the following topic: $input. " +
                    "Break down the report into main sections and subsections (only if neceessary), " +
                    "providing a brief description of what each section will cover. " +
                    "Format the plan in markdown with sections as H2 and subsections as H3." +
                    "Dont write more than 5 sections. With at MOST 2 sub-sections" +
                    "Dont include unnecessary text, only the outline of the report."
            )
        }
        val response = requestLLMWithoutTools()
        storage.set(reportPlanStorage, response.content)
        input
        }
    }


    val nodeSearchByQuery by node<String, String>("search-web") {
            query ->
        llm.writeSession {
            updatePrompt {
                user("""
                    Your task is to gather information usıng the websearch tool. Search the web using the following query: "$query".
                    Format the result of the websearch to a readable text.
                    """.trimIndent()
                )
            }
            val response = requestLLMForceOneTool(toolRegistry.getTool("web-search"))
            response.content
        }
    }


    val nodeWriteSection by subgraphWithVerification<String>(
        tools = ReportCreationTools().asTools()
    ) {
            input -> """
                You are given as input the information gathered from websearch for the report sections and the report plan stored in the storage.
                Your task is to write each section of the report in MARKDOWN format using the report creation tools provided.
                Make sure to write each section based on the information gathered from websearch and the report plan.
                Use the "writeSection" tool to write each section of the report.
                After writing each section, use the "reportContent" tool to get the current content of the report.
                Continue this process until all sections of the report are written.
                In the end, the report should be a comprehensive and well-structured document that covers all
                sections outlined in the report plan.
                Make sure you search the web once for the entirety of a section before writing it. Create your query based on the section title and description.
                Dont make unnecessary web searches for each subsection.
                
                Be concise and avoid unnecessary repetition.
                
                If you believe the report is complete, you can verify it as correct to finish pass to finish the task.
                If you need a web search to gather more information, mark it as incorrect to go back to the web search step, respond only 
                with the search query.
                
                # Report Plan:
                ${storage.get(reportPlanStorage)}
                # Information for the section to write:
                $input
            """.trimIndent()
    }

    edge(nodeStart forwardTo nodeCreateReportPlan)
    edge(nodeCreateReportPlan forwardTo nodeWriteSection )
    edge(
        nodeWriteSection forwardTo nodeSearchByQuery
                onCondition {res -> !res.correct}
                transformed {res -> res.message}
    )
    edge(nodeSearchByQuery forwardTo nodeWriteSection )
    edge(nodeWriteSection forwardTo nodeFinish
            onCondition {res -> res.correct}
            transformed {subgraphResult -> subgraphResult.message})
}

/**

 *
 * +-------+             +---------------+             +---------------+             +--------+
 * | Start | ----------> | CallLLMReason | ----------> | CallLLMAction | ----------> | Finish |
 * +-------+             +---------------+             +---------------+             +--------+
 *                                   ^                       | Finished?     Yes
 *                                   |                       | No
 *                                   |                       v
 *                                   +-----------------------+
 *                                   |      ExecuteTool      |
 *                                   +-----------------------+

 * NOT USED, ONLY FOR TESTING PURPOSES
 */

fun reActModifiedWebSearchStrategy(
    reasoningInterval: Int = 1,
    name: String = "reActModifiedWebSearchStrategy"
): AIAgentStrategy<String, String> = strategy(name) {

    require(reasoningInterval > 0) { "Reasoning interval must be greater than 0" }

    val reasoningStepKey = createStorageKey<Int>("reasoning_step")
    val nodeSetup by node<String, String> {
        storage.set(reasoningStepKey, 0)
        it
    }

    val nodeCallLLM by node<Unit, Message.Response> {
        llm.writeSession {
            requestLLM()
        }
    }

    val nodeExecuteTool by nodeExecuteTool("Execute tools")

    val reasoningPrompt = "Give your thoughts about the task and plan the next steps."

    val nodeCallLLMReasonInput by node<String, Unit> { stageInput ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
                user(reasoningPrompt)
            }

            requestLLMWithoutTools()
        }
    }
    val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
        val reasoningStep = storage.getValue(reasoningStepKey)
        llm.writeSession {
            updatePrompt {
                tool {
                    result(result)
                }
            }

            if (reasoningStep % reasoningInterval == 0) {
                updatePrompt {
                    user(reasoningPrompt)
                }
                requestLLMWithoutTools()
            }
        }
        storage.set(reasoningStepKey, reasoningStep + 1)
    }

    edge(nodeStart forwardTo nodeSetup)
    edge(nodeSetup forwardTo nodeCallLLMReasonInput)
    edge(nodeCallLLMReasonInput forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeCallLLMReason)
    edge(nodeCallLLMReason forwardTo nodeCallLLM)
}