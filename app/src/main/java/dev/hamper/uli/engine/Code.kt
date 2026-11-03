package dev.hamper.uli.engine

/**
 * Finds what looks like shell code in prose so the UI can set it in a monospace face:
 * paths, flags, variables, globs, redirections and well-known command names.
 */
object Code {
    private val commands = setOf(
        "ls", "cp", "mv", "rm", "cd", "pwd", "chmod", "chown", "chgrp", "tar", "gzip", "gunzip", "bzip2", "xz",
        "zip", "unzip", "grep", "egrep", "echo", "ln", "usermod", "useradd", "userdel", "groupmod", "groupadd",
        "id", "who", "w", "su", "sudo", "ps", "df", "top", "man", "less", "vim", "nano", "locate", "updatedb",
        "whereis", "whatis", "apropos", "ifconfig", "ipconfig", "ip", "ss", "netstat", "dig", "ssh", "env",
        "sysctl", "gdisk", "sgdisk", "cgdisk", "sfdisk", "fdisk", "lsmod", "chage", "getent", "printf", "cat",
        "tee", "info", "history", "test", "bash", "csh", "tcsh", "ksh", "zsh", "sh", "groups", "touch", "mkdir",
        "printenv", "export", "alias", "type", "which", "head", "tail", "sort", "wc", "find", "free", "cmdline",
    )

    /** Command names that also show up as plain words inside Slovak prose ("free" software, "info"). */
    private val ambiguous = setOf("free", "test", "info", "type", "find", "sort", "less", "head", "tail", "export", "alias", "touch", "top")

    private val trailing = charArrayOf('.', ',', ':', ';', ')', '“', '”', '"', '?')
    private val leading = charArrayOf('(', '„', '“', '"')

    private fun isSymbolic(t: String): Boolean {
        if (t.isEmpty()) return false
        if (t in setOf("/", "^", "$", "|", ";", "!", "&&", "||", ">", "<", "~", "#!", "*")) return true
        if (t.length == 1) return false
        return t.startsWith("/") || t.startsWith("~/") || t.startsWith("./") ||
            (t.startsWith("-") && t.length in 2..12 && t[1].isLetter() || t.startsWith("--")) ||
            t.startsWith("$") || t.startsWith("^") || t.startsWith("#!") || t.startsWith("!") ||
            (t.startsWith(".") && t[1].isLetter()) ||
            t.contains("[") || t.contains("*") || t.contains(">") || t.contains("|") ||
            assignment.matches(t)
    }

    private val assignment = Regex("^[A-Za-z_][A-Za-z0-9_]*=\\S*$")

    private data class Token(val start: Int, val end: Int, val core: String, val coreStart: Int)

    private fun tokens(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break
            val s = i
            while (i < text.length && !text[i].isWhitespace()) i++
            var a = s
            var b = i
            while (a < b && text[a] in leading) a++
            while (b > a && text[b - 1] in trailing && !(b - a == 1)) b--
            out += Token(s, i, text.substring(a, b), a)
        }
        return out
    }

    /**
     * Character ranges (end exclusive) to render as code. [english] prose only marks a command name
     * when a flag or path follows it, or when it is followed by the word "command".
     */
    fun spans(text: String, english: Boolean): List<IntRange> {
        val toks = tokens(text)
        val code = BooleanArray(toks.size)
        for ((i, t) in toks.withIndex()) {
            if (isSymbolic(t.core)) code[i] = true
        }
        for ((i, t) in toks.withIndex()) {
            if (code[i] || t.core !in commands) continue
            val next = toks.getOrNull(i + 1)
            val nextIsCode = next != null && isSymbolic(next.core)
            val sentenceStart = i == 0 || text[toks[i - 1].end - 1] in ".:"
            code[i] = when {
                english -> nextIsCode || next?.core?.lowercase() == "command"
                t.core in ambiguous -> nextIsCode || next?.core == "=" || sentenceStart && t.start == t.coreStart
                else -> true
            }
        }
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < toks.size) {
            if (!code[i]) { i++; continue }
            val start = toks[i].coreStart
            var j = i
            while (j + 1 < toks.size && code[j + 1] && !endsClause(text, toks[j]) && toks[j + 1].start == toks[j + 1].coreStart) j++
            val last = toks[j]
            ranges += start until (last.coreStart + last.core.length)
            i = j + 1
        }
        return ranges
    }

    private fun endsClause(text: String, t: Token): Boolean =
        t.coreStart + t.core.length < t.end && text[t.coreStart + t.core.length] in charArrayOf(',', ':', ';', ')', '.')

    /** Option texts that are commands, paths, flags or keys, when most options of the question are. */
    fun codeOptions(q: Question): Set<Int> {
        if (q.kind == Kind.TrueFalse) return emptySet()
        val looks = q.options.map { looksLikeCode(it.text) }
        return if (looks.count { it } * 2 >= q.options.size) q.options.indices.filter { looks[it] }.toSet() else emptySet()
    }

    fun looksLikeCode(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 40) return false
        if (t.all { it.isDigit() || it == ',' }) return false
        if (t.length <= 2) return true
        val first = t.first()
        return first == '/' || first == '-' || first == '~' || first == '^' ||
            (first.isLowerCase() && first.isLetter())
    }
}
