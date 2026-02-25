package com.example.continuum_explorer.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.continuum_explorer.model.UniversalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

object SearchManager {

    suspend fun search(
        context: Context,
        query: String,
        currentPath: File?,
        currentSafUri: Uri?,
        searchSubfolders: Boolean,
        archiveCache: Map<String, List<UniversalFile>>?,
        currentArchivePath: String
    ): List<UniversalFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<UniversalFile>()
        val parsedQuery = parseQuery(query)

        if (archiveCache != null) {
            // Search in archive cache
            val startPath = currentArchivePath.removeSuffix("/")
            for ((path, files) in archiveCache) {
                if (!isActive) break
                val isSubfolder = path.startsWith(if (startPath.isEmpty()) "" else "$startPath/")
                val isExactFolder = path == startPath
                if (isExactFolder || (searchSubfolders && isSubfolder)) {
                    for (file in files) {
                        if (matches(file, parsedQuery)) {
                            results.add(file)
                        }
                    }
                }
            }
        } else if (currentPath != null) {
            // Search in normal file system
            searchDirectory(currentPath, parsedQuery.expression, searchSubfolders, results)
        } else if (currentSafUri != null) {
            // Search in SAF
            val docFile = DocumentFile.fromTreeUri(context, currentSafUri)
            if (docFile != null) {
                searchSaf(docFile, parsedQuery.expression, searchSubfolders, results)
            }
        }

        results
    }

    private fun searchDirectory(dir: File, expr: SearchExpression, recursive: Boolean, results: MutableList<UniversalFile>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name == ".metadata") continue
            val universalFile = file.toUniversal()
            if (evaluateExpression(universalFile, expr)) {
                results.add(universalFile)
            }
            if (recursive && file.isDirectory) {
                searchDirectory(file, expr, recursive, results)
            }
        }
    }

    private fun searchSaf(dir: DocumentFile, expr: SearchExpression, recursive: Boolean, results: MutableList<UniversalFile>) {
        val files = dir.listFiles()
        for (file in files) {
            val universalFile = file.toUniversal()
            if (evaluateExpression(universalFile, expr)) {
                results.add(universalFile)
            }
            if (recursive && file.isDirectory) {
                searchSaf(file, expr, recursive, results)
            }
        }
    }

    private fun matches(file: UniversalFile, query: ParsedQuery): Boolean {
        // Evaluate the boolean expression
        return evaluateExpression(file, query.expression)
    }

    private fun evaluateExpression(file: UniversalFile, expr: SearchExpression): Boolean {
        return when (expr) {
            is SearchExpression.Term -> matchTerm(file, expr.term)
            is SearchExpression.And -> evaluateExpression(file, expr.left) && evaluateExpression(file, expr.right)
            is SearchExpression.Or -> evaluateExpression(file, expr.left) || evaluateExpression(file, expr.right)
            is SearchExpression.Not -> evaluateExpression(file, expr.left) && !evaluateExpression(file, expr.right)
        }
    }

    private fun matchTerm(file: UniversalFile, term: String): Boolean {
        val lowerTerm = term.lowercase()
        if (lowerTerm.startsWith("kind:")) {
            val kind = lowerTerm.substring(5).lowercase()
            val fileKind = getFileKind(file)
            return fileKind == kind || fileKind.contains(kind)
        } else if (lowerTerm.contains("*") || lowerTerm.contains("?")) {
            val regexBuilder = java.lang.StringBuilder()
            regexBuilder.append("^")
            for (c in lowerTerm) {
                when (c) {
                    '*' -> regexBuilder.append(".*")
                    '?' -> regexBuilder.append(".")
                    '.', '^', '$', '+', '-', '|', '[', ']', '(', ')', '{', '}', '\\' -> {
                        regexBuilder.append('\\').append(c)
                    }
                    else -> regexBuilder.append(c)
                }
            }
            regexBuilder.append("$")
            return try {
                val regex = Regex(regexBuilder.toString())
                regex.matches(file.name.lowercase())
            } catch (e: Exception) {
                false
            }
        } else {
            return file.name.lowercase().contains(lowerTerm)
        }
    }

    private fun getFileKind(file: UniversalFile): String {
        if (file.isDirectory) return "folder"
        val ext = file.name.substringAfterLast('.', "").lowercase()
        
        // Custom broad categories
        val archiveExtensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2")
        if (archiveExtensions.contains(ext)) return "archive"
        
        val documentExtensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
        if (documentExtensions.contains(ext)) return "document"

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: return "unknown"
        return mimeType.split("/")[0] // e.g., "video", "image", "audio", "text", "application"
    }

    // Query Parsing
    
    sealed class SearchExpression {
        data class Term(val term: String) : SearchExpression()
        data class And(val left: SearchExpression, val right: SearchExpression) : SearchExpression()
        data class Or(val left: SearchExpression, val right: SearchExpression) : SearchExpression()
        data class Not(val left: SearchExpression, val right: SearchExpression) : SearchExpression()
    }

    data class ParsedQuery(val expression: SearchExpression)

    private fun parseQuery(queryString: String): ParsedQuery {
        val tokens = tokenize(queryString)
        if (tokens.isEmpty()) return ParsedQuery(SearchExpression.Term(""))
        val expr = parseExpression(tokens)
        return ParsedQuery(expr)
    }

    private fun tokenize(query: String): List<String> {
        val tokens = mutableListOf<String>()
        val matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(query)
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                tokens.add(matcher.group(1)!!)
            } else {
                tokens.add(matcher.group(2)!!)
            }
        }
        return tokens
    }

    private fun parseExpression(tokens: List<String>): SearchExpression {
        if (tokens.isEmpty()) return SearchExpression.Term("")
        
        var currentExpr: SearchExpression = SearchExpression.Term(tokens[0])
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i].uppercase()
            if (op == "AND" || op == "OR" || op == "NOT") {
                if (i + 1 < tokens.size) {
                    val nextTerm = SearchExpression.Term(tokens[i + 1])
                    currentExpr = when (op) {
                        "AND" -> SearchExpression.And(currentExpr, nextTerm)
                        "OR" -> SearchExpression.Or(currentExpr, nextTerm)
                        "NOT" -> SearchExpression.Not(currentExpr, nextTerm)
                        else -> currentExpr
                    }
                    i += 2
                } else {
                    break
                }
            } else {
                // Implicit AND
                val nextTerm = SearchExpression.Term(tokens[i])
                currentExpr = SearchExpression.And(currentExpr, nextTerm)
                i += 1
            }
        }
        return currentExpr
    }

    private fun File.toUniversal() = UniversalFile(
        name = name,
        isDirectory = isDirectory,
        lastModified = lastModified(),
        length = length(),
        fileRef = this,
        absolutePath = absolutePath
    )

    private fun DocumentFile.toUniversal() = UniversalFile(
        name = name ?: "Unknown",
        isDirectory = isDirectory,
        lastModified = lastModified(),
        length = length(),
        documentFileRef = this,
        absolutePath = uri.toString()
    )
}