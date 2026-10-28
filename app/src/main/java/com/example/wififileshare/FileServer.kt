package com.example.wififileshare

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileServer(private val rootDir: File, port: Int = 8080) : NanoHTTPD(port) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parameters

        return try {
            when {
                uri == "/" || uri.startsWith("/browse") -> handleBrowse(params)
                uri.startsWith("/download") -> handleFileTransfer(params, asDownload = true)
                uri.startsWith("/view") -> handleFileTransfer(params, asDownload = false)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error: ${e.message}")
        }
    }

    private fun handleBrowse(params: Map<String, List<String>>): Response {
        val relativePath = params["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
        val currentDir = if (relativePath.isBlank()) rootDir else File(rootDir, relativePath)

        // Pencegahan Path Traversal: pastikan path berada di dalam rootDir
        if (!currentDir.canonicalPath.startsWith(rootDir.canonicalPath)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Akses ditolak: di luar direktori berbagi.")
        }

        if (!currentDir.exists() || !currentDir.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Folder tidak ditemukan atau bukan direktori.")
        }

        val allItems = currentDir.listFiles() ?: arrayOf()
        val folders = allItems.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
        val files = allItems.filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }

        val html = buildPageHtml(currentDir, relativePath, folders, files)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleFileTransfer(params: Map<String, List<String>>, asDownload: Boolean): Response {
        val relativePath = params["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
        val targetFile = File(rootDir, relativePath)

        // Keamanan path traversal
        if (!targetFile.canonicalPath.startsWith(rootDir.canonicalPath)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Akses ditolak.")
        }

        if (!targetFile.exists() || !targetFile.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Berkas tidak ditemukan.")
        }

        val mimeType = getMimeType(targetFile.extension)
        val fis = FileInputStream(targetFile)

        val dispositionType = if (asDownload) "attachment" else "inline"
        val encodedFilename = URLEncoder.encode(targetFile.name, "UTF-8").replace("+", "%20")

        return newFixedLengthResponse(
            Response.Status.OK,
            mimeType,
            fis,
            targetFile.length()
        ).apply {
            addHeader("Content-Disposition", "$dispositionType; filename=\"$encodedFilename\"; filename*=UTF-8''$encodedFilename")
            addHeader("Accept-Ranges", "bytes")
        }
    }

    private fun buildPageHtml(
        currentDir: File,
        currentRelativePath: String,
        folders: List<File>,
        files: List<File>
    ): String {
        val breadcrumbsHtml = buildBreadcrumbs(currentRelativePath)
        val hasParent = currentRelativePath.isNotBlank()
        val parentPath = if (hasParent) {
            val parentFile = File(currentRelativePath).parent ?: ""
            URLEncoder.encode(parentFile, "UTF-8")
        } else ""

        val rowsHtml = StringBuilder()

        if (hasParent) {
            rowsHtml.append("""
                <tr>
                    <td>
                        <div class="name-col">
                            <span class="icon">📁</span>
                            <a class="item-link" href="/browse?path=$parentPath"><strong>.. [Folder Sebelumnya]</strong></a>
                        </div>
                    </td>
                    <td>-</td>
                    <td>-</td>
                    <td></td>
                </tr>
            """.trimIndent())
        }

        for (folder in folders) {
            val relPath = if (currentRelativePath.isBlank()) folder.name else "$currentRelativePath/${folder.name}"
            val encodedPath = URLEncoder.encode(relPath, "UTF-8")
            val itemCount = folder.listFiles()?.size ?: 0
            val dateStr = dateFormat.format(Date(folder.lastModified()))

            rowsHtml.append("""
                <tr>
                    <td>
                        <div class="name-col">
                            <span class="icon">📁</span>
                            <a class="item-link item-name" href="/browse?path=$encodedPath">${escapeHtml(folder.name)}</a>
                        </div>
                    </td>
                    <td>$itemCount item</td>
                    <td>$dateStr</td>
                    <td>
                        <a class="btn-pill" href="/browse?path=$encodedPath">Buka</a>
                    </td>
                </tr>
            """.trimIndent())
        }

        for (file in files) {
            val relPath = if (currentRelativePath.isBlank()) file.name else "$currentRelativePath/${file.name}"
            val encodedPath = URLEncoder.encode(relPath, "UTF-8")
            val sizeStr = formatFileSize(file.length())
            val dateStr = dateFormat.format(Date(file.lastModified()))
            val fileIcon = getFileIcon(file.extension)
            val isMedia = isPreviewable(file.extension)

            rowsHtml.append("""
                <tr>
                    <td>
                        <div class="name-col">
                            <span class="icon">$fileIcon</span>
                            <span class="item-name">${escapeHtml(file.name)}</span>
                        </div>
                    </td>
                    <td>$sizeStr</td>
                    <td>$dateStr</td>
                    <td>
                        <div class="btn-group">
                            ${if (isMedia) "<a class=\"btn-pill\" target=\"_blank\" href=\"/view?path=$encodedPath\">Lihat</a>" else ""}
                            <a class="btn-pill btn-pill-solid" href="/download?path=$encodedPath">Unduh</a>
                        </div>
                    </td>
                </tr>
            """.trimIndent())
        }

        val emptyNotice = if (folders.isEmpty() && files.isEmpty()) {
            "<div class='empty-state'>[ Tidak ada berkas di dalam folder ini ]</div>"
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>WiFi Share</title>
                <style>
                    /* Minimalist Modern 2-Color Architecture */
                    :root {
                        --black: #000000;
                        --white: #ffffff;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Geist", "Segoe UI", Roboto, sans-serif;
                        background: var(--white);
                        color: var(--black);
                        padding: 32px 20px;
                        line-height: 1.5;
                    }
                    .container {
                        max-width: 960px;
                        margin: 0 auto;
                    }
                    .header {
                        padding-bottom: 24px;
                        margin-bottom: 24px;
                        border-bottom: 1px solid var(--black);
                    }
                    .header-top {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 16px;
                    }
                    .header h1 {
                        font-size: 1.5rem;
                        font-weight: 700;
                        letter-spacing: -0.03em;
                    }
                    .badge-pill {
                        display: inline-block;
                        padding: 4px 14px;
                        border-radius: 9999px;
                        font-size: 0.75rem;
                        font-weight: 700;
                        letter-spacing: 0.05em;
                        border: 1px solid var(--black);
                        background: var(--black);
                        color: var(--white);
                    }
                    .breadcrumbs {
                        display: flex;
                        flex-wrap: wrap;
                        align-items: center;
                        gap: 8px;
                        font-size: 0.9rem;
                        margin-bottom: 16px;
                    }
                    .breadcrumbs a {
                        color: var(--black);
                        text-decoration: underline;
                        font-weight: 600;
                    }
                    .search-input {
                        width: 100%;
                        padding: 10px 16px;
                        border: 1px solid var(--black);
                        border-radius: 9999px;
                        font-size: 0.9rem;
                        outline: none;
                        background: var(--white);
                        color: var(--black);
                    }
                    .search-input::placeholder {
                        color: #666666;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        text-align: left;
                        border-top: 1px solid var(--black);
                    }
                    th {
                        padding: 12px 14px;
                        font-size: 0.75rem;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                        border-bottom: 1px solid var(--black);
                    }
                    td {
                        padding: 12px 14px;
                        border-bottom: 1px solid #e5e7eb;
                        font-size: 0.9rem;
                        vertical-align: middle;
                    }
                    tr:hover td {
                        background: #f9f9f9;
                    }
                    .name-col {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        word-break: break-all;
                    }
                    .icon {
                        font-size: 1.2rem;
                    }
                    .item-link {
                        color: var(--black);
                        text-decoration: none;
                    }
                    .item-link:hover {
                        text-decoration: underline;
                    }
                    .btn-group {
                        display: flex;
                        gap: 6px;
                    }
                    .btn-pill {
                        display: inline-block;
                        padding: 6px 14px;
                        border-radius: 9999px;
                        font-size: 0.8rem;
                        font-weight: 600;
                        text-decoration: none;
                        border: 1px solid var(--black);
                        background: var(--white);
                        color: var(--black);
                        transition: all 0.15s ease;
                        cursor: pointer;
                    }
                    .btn-pill:hover {
                        background: var(--black);
                        color: var(--white);
                    }
                    .btn-pill-solid {
                        background: var(--black);
                        color: var(--white);
                    }
                    .btn-pill-solid:hover {
                        background: var(--white);
                        color: var(--black);
                    }
                    .empty-state {
                        padding: 40px;
                        text-align: center;
                        font-size: 0.9rem;
                        color: #666666;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="header-top">
                            <h1>WiFi Share</h1>
                            <span class="badge-pill">TERHUBUNG LOKAL</span>
                        </div>
                        <div class="breadcrumbs">
                            $breadcrumbsHtml
                        </div>
                        <input type="text" class="search-input" id="searchInput" placeholder="Cari berkas atau folder..." onkeyup="filterList()">
                    </div>

                    <table>
                        <thead>
                            <tr>
                                <th>Nama</th>
                                <th style="width: 110px;">Ukuran</th>
                                <th style="width: 160px;">Tanggal</th>
                                <th style="width: 150px;">Aksi</th>
                            </tr>
                        </thead>
                        <tbody id="fileTableBody">
                            $rowsHtml
                        </tbody>
                    </table>
                    $emptyNotice
                </div>

                <script>
                    function filterList() {
                        const filter = document.getElementById('searchInput').value.toLowerCase();
                        const rows = document.querySelectorAll('#fileTableBody tr');
                        rows.forEach(row => {
                            const nameElem = row.querySelector('.item-name');
                            if (!nameElem) return;
                            const text = nameElem.textContent.toLowerCase();
                            row.style.display = text.includes(filter) ? '' : 'none';
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildBreadcrumbs(currentRelativePath: String): String {
        if (currentRelativePath.isBlank()) {
            return "<span>🏠 Root</span>"
        }

        val parts = currentRelativePath.split("/").filter { it.isNotBlank() }
        val sb = StringBuilder()
        sb.append("<a href=\"/browse?path=\">🏠 Root</a>")

        var cumulative = ""
        for (part in parts) {
            cumulative = if (cumulative.isEmpty()) part else "$cumulative/$part"
            val encoded = URLEncoder.encode(cumulative, "UTF-8")
            sb.append(" <span>/</span> ")
            sb.append("<a href=\"/browse?path=$encoded\">${escapeHtml(part)}</a>")
        }

        return sb.toString()
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun getFileIcon(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "svg" -> "🖼️"
            "mp4", "mkv", "mov", "avi", "webm" -> "🎬"
            "mp3", "wav", "flac", "ogg", "m4a" -> "🎵"
            "pdf" -> "📕"
            "doc", "docx" -> "📝"
            "xls", "xlsx" -> "📊"
            "ppt", "pptx" -> "📑"
            "zip", "rar", "7z", "tar", "gz" -> "📦"
            "apk" -> "🤖"
            "txt", "log", "json", "xml" -> "📄"
            else -> "📄"
        }
    }

    private fun isPreviewable(extension: String): Boolean {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "mp3", "wav", "pdf", "txt" -> true
            else -> false
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> "text/html"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
