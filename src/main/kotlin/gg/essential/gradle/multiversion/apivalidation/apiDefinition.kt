package gg.essential.gradle.multiversion.apivalidation

class Parser(
    private val defaultProjects: Set<String>
) {
    fun parseFile(str: String): ApiFile {
        val normalizedStr = str.lineSequence().joinToString("\n")
        val classes = normalizedStr.split("\n\n").mapNotNull(::parseClass)
        return ApiFile(classes.toMutableList())
    }

    private fun parseClass(str: String): ApiClass? {
        val parts = str.split("{", "}").map { it.trim() }
        if (parts.size < 2) {
            return null
        }
        val (header, body) = parts
        val (projects, desc) = if (header.startsWith("@")) {
            val (projects, desc) = header.split("\n", limit = 2)
            parseProjects(projects) to desc
        } else {
            Projects(defaultProjects.toMutableSet()) to header
        }
        var annotation: String? = null
        val members = body
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                // Merge back together annotations with the line following them
                when {
                    line.startsWith("@") -> null.also { annotation = line }
                    annotation != null -> "$annotation\n$line".also { annotation = null }
                    else -> line
                }
            }
            .map { parseMember(it, projects) }
            .toMutableList()
        return ApiClass(desc, members, projects)
    }

    private fun parseMember(str: String, defaultProjects: Set<String>): ApiMember {
        return if (str.startsWith("@")) {
            val (projects, desc) = str.split("\n", limit = 2)
            ApiMember(desc, parseProjects(projects))
        } else {
            ApiMember(str, Projects(defaultProjects.toMutableSet()))
        }
    }

    private fun parseProjects(str: String) = Projects(str.trim('@', ' ').split(",").toMutableSet())
}

class Writer(
    private val defaultProjects: Set<String>,
) {
    fun write(file: ApiFile) = file.str().lines().joinToString(System.lineSeparator())

    private fun ApiFile.str() = classes.joinToString("") { it.str() + "\n\n" }

    private fun ApiClass.str(): String = listOf(
        listOf(projects.str(defaultProjects)),
        listOf("$header {"),
        members.map { it.str(projects) },
        listOf("}"),
    ).flatten().filterNotNull().joinToString("\n")

    private fun ApiMember.str(defaultProjects: Set<String>): String =
        listOfNotNull(projects.str(defaultProjects), desc).joinToString("\n") { "\t$it" }

    private fun Projects.str(defaultProjects: Set<String>): String? =
        if (set == defaultProjects) null else joinToString(",", prefix = "@")
}

class ApiFile(val classes: MutableList<ApiClass>) {
    fun filtered(project: String): ApiFile = ApiFile(classes.mapNotNull { it.filtered(project) }.toMutableList())

    fun mergeInto(targetFile: ApiFile, project: String) {
        var prevIndex = -1
        for (fromClass in classes) {
            val index = targetFile.classes.indexOfFirst { it.header == fromClass.header }
            val targetClass = if (index == -1) {
                ApiClass(fromClass.header, fromClass.members).also {
                    targetFile.classes.add(prevIndex + 1, it)
                }
            } else {
                targetFile.classes[index]
            }
            prevIndex = targetFile.classes.indexOf(targetClass)

            targetClass.projects.add(project)
            fromClass.mergeInto(targetClass, project)
        }
    }
}

class ApiClass(
    val header: String,
    val members: MutableList<ApiMember>,
    val projects: Projects = Projects(),
) {
    fun filtered(project: String) = if (project in projects) {
        ApiClass(header, members.mapNotNull { it.filtered(project) }.toMutableList())
    } else {
        null
    }

    fun mergeInto(targetClass: ApiClass, project: String) {
        var prevIndex = -1
        for (fromMember in members) {
            val index = targetClass.members.indexOfFirst { it.desc == fromMember.desc }
            val targetMember = if (index == -1) {
                ApiMember(fromMember.desc).also {
                    targetClass.members.add(prevIndex + 1, it)
                }
            } else {
                targetClass.members[index]
            }
            prevIndex = targetClass.members.indexOf(targetMember)

            targetMember.projects.add(project)
        }
    }
}

class ApiMember(val desc: String, val projects: Projects = Projects()) {
    fun filtered(project: String) = if (project in projects) ApiMember(desc) else null
}

class Projects(val set: MutableSet<String> = mutableSetOf()) : MutableSet<String> by set
