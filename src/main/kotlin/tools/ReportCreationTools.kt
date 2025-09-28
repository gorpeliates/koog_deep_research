package org.jbtasks.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File

@LLMDescription("Tools for creating and managing reports in MARKDOWN format.")
class ReportCreationTools : ToolSet {

    private val report = File("report.md")

    @Tool
    @LLMDescription("Initializes a new report file, overwriting any existing file.")
    fun initReport(): String {
        if (report.exists()) {
            report.writeText("") // Clear existing content
            return "Existing report file cleared."
        } else {
            report.createNewFile()
            return "New report file created."
        }
    }

    @Tool
    @LLMDescription("Appends a section to the report.")
    fun writeSection(
        @LLMDescription("Section title.")
        title: String,
        @LLMDescription("Section text to append")
        section: String
    ): String {
        report.appendText("## $title\n")
        report.appendText("$section\n\n")

        return "Section with title '$title' added to the report."
    }

}