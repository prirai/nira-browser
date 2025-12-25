package com.prirai.android.nira.settings.fragment

import com.prirai.android.nira.preferences.UserPreferences

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.utils.BookmarkUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Scanner


class ImportExportSettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_import_export)

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_import_bookmarks),
            onClick = { requestBookmarkImport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_export_bookmarks),
            onClick = { requestBookmarkExport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_clear_bookmarks),
            onClick = { clearBookmarks() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_import_settings),
            onClick = { requestSettingsImport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_export_settings),
            onClick = { requestSettingsExport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_clear_settings),
            onClick = { clearSettings() }
        )
    }

    private fun clearBookmarks() {
        // TODO: notify bookmark manager to clear bookmarks
        val builder = MaterialAlertDialogBuilder(activity as Activity)
        builder.setTitle(getString(R.string.clear_bookmarks))
        builder.setMessage(getString(R.string.clear_bookmarks_confirm))

        builder.setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { dialogInterface, which ->
            Toast.makeText(
                activity,
                R.string.successful, Toast.LENGTH_LONG
            ).show()

            val manager = BookmarkManager.getInstance(requireActivity())
            manager.file.delete()
            manager.initialize()
        }
        builder.setNegativeButton(resources.getString(R.string.cancel)) { dialogInterface, which ->

        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
    }

    private fun requestBookmarkImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, IMPORT_BOOKMARKS)
    }

    private fun requestBookmarkExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_TITLE, "BookmarkExport.txt")
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, EXPORT_BOOKMARKS)
    }

    private fun requestSettingsImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, IMPORT_SETTINGS)
    }

    private fun requestSettingsExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_TITLE, "SettingsExport.txt")
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, EXPORT_SETTINGS)
    }

    private fun importBookmarks(uri: Uri) {
        val manager = BookmarkManager.getInstance(requireActivity())
        val bookmarkFile = manager.file

        val input: InputStream? = requireActivity().contentResolver.openInputStream(uri)
        val content = input?.bufferedReader().use { it?.readText() }

        if (context?.contentResolver?.getType(uri) == "text/html") {
            bookmarkFile.delete()
            manager.initialize()

            val lines = content?.split("\n") ?: listOf()

            val folderStack = mutableListOf<BookmarkFolderItem>()
            folderStack.add(manager.root)

            var lastFolder: BookmarkFolderItem? = null
            var lastFolderName = ""

            for (line in lines) {
                val trimmedLine = line.trim()

                // Skip HR tags
                if (trimmedLine.startsWith("<HR", ignoreCase = true)) {
                    continue
                }

                // Check for folder (H3 tag)
                if (trimmedLine.contains("<H3", ignoreCase = true)) {
                    val startIdx = trimmedLine.indexOf('>', trimmedLine.indexOf("<H3", ignoreCase = true)) + 1
                    val endIdx = trimmedLine.indexOf("</H3>", ignoreCase = true)

                    if (startIdx > 0 && endIdx > startIdx) {
                        lastFolderName = trimmedLine.substring(startIdx, endIdx).trim()

                        if (lastFolderName.isNotEmpty()) {
                            val currentParent = folderStack.lastOrNull() ?: manager.root
                            lastFolder = BookmarkFolderItem(
                                lastFolderName,
                                currentParent,
                                BookmarkUtils.getNewId()
                            )
                            currentParent.add(lastFolder!!)
                        }
                    }
                }
                // Check for opening DL tag (start of folder contents)
                else if (trimmedLine.startsWith("<DL", ignoreCase = true)) {
                    if (lastFolder != null) {
                        folderStack.add(lastFolder!!)
                        lastFolder = null
                    }
                }
                // Check for closing DL tag (end of folder contents)
                else if (trimmedLine.startsWith("</DL>", ignoreCase = true)) {
                    if (folderStack.size > 1) {
                        folderStack.removeAt(folderStack.size - 1)
                    }
                }
                // Check for bookmark (A tag)
                else if (trimmedLine.contains("<A", ignoreCase = true) &&
                    trimmedLine.contains("HREF", ignoreCase = true)
                ) {

                    // Extract URL
                    val hrefStart = trimmedLine.indexOf("HREF=\"", ignoreCase = true)
                    if (hrefStart >= 0) {
                        val urlStart = hrefStart + 6
                        val urlEnd = trimmedLine.indexOf('"', urlStart)

                        if (urlEnd > urlStart) {
                            val url = trimmedLine.substring(urlStart, urlEnd)

                            // Extract title
                            val titleStart = trimmedLine.indexOf('>', hrefStart) + 1
                            val titleEnd = trimmedLine.indexOf("</A>", ignoreCase = true)

                            val title = if (titleEnd > titleStart) {
                                trimmedLine.substring(titleStart, titleEnd).trim()
                            } else {
                                url
                            }

                            if (url.isNotEmpty()) {
                                val bookmark = BookmarkSiteItem(
                                    title,
                                    url,
                                    BookmarkUtils.getNewId()
                                )
                                val currentParent = folderStack.lastOrNull() ?: manager.root
                                manager.add(currentParent, bookmark)
                            }
                        }
                    }
                }
            }

            manager.save()

            Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
        } else {

            val itemArray = JSONTokener(content).nextValue()

            // If the imported file is JSON and is an array, assume it's an export from this browser, or if it's an object, assume it's a legacy export
            if (itemArray is JSONArray) {
                val bookmarks = FileOutputStream(bookmarkFile, false)
                val contents: ByteArray = content?.toByteArray() ?: byteArrayOf()
                bookmarks.write(contents)
                bookmarks.flush()
                bookmarks.close()

                manager.initialize()
            } else if (itemArray is JSONObject) {
                bookmarkFile.delete()
                manager.initialize()

                val folderScanner = Scanner(content)
                val folderArray = mutableListOf<String>()

                // Iterate over all folder items in the bookmark list and create them
                while (folderScanner.hasNextLine()) {
                    val line = folderScanner.nextLine()
                    val `object` = JSONObject(line)
                    val folderName: String = `object`.getString(KEY_FOLDER)

                    if (folderName != "") {
                        folderArray.add(folderName)
                    }
                }

                val uniqueFolderArray = folderArray.distinct()
                val folderItemArray = mutableListOf<BookmarkFolderItem>()

                for (i in uniqueFolderArray) {
                    val newFolder = BookmarkFolderItem(i, manager.root, BookmarkUtils.getNewId())
                    folderItemArray.add(newFolder)
                    manager.root.add(newFolder)
                }

                val scanner = Scanner(content)

                // Iterate over bookmarks and add them to relevant folders based on names
                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine()
                    val `object` = JSONObject(line)
                    val folderName: String = `object`.getString(KEY_FOLDER)

                    var folder = manager.root

                    // Not the best way to do this, but it should be OK because names are unique in the old bookmark system
                    if (folderName != "") {
                        folder = folderItemArray[uniqueFolderArray.indexOf(folderName)]
                    }

                    val entry: BookmarkItem = BookmarkSiteItem(
                        `object`.getString(KEY_TITLE),
                        `object`.getString(KEY_URL),
                        BookmarkUtils.getNewId()
                    )

                    manager.add(folder, entry)
                    manager.save()
                }

                scanner.close()
            } else {
                Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                return
            }
        }

        Toast.makeText(
            context,
            requireContext().resources.getText(R.string.app_restart),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun exportBookmarks(uri: Uri) {
        val manager = BookmarkManager.getInstance(requireActivity())
        val bookmarkFile = manager.file

        val output: OutputStream? = requireActivity().contentResolver.openOutputStream(uri)
        output?.write(bookmarkFile.readBytes())
        output?.flush()
        output?.close()
        Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
    }

    private fun clearSettings() {
        val builder = MaterialAlertDialogBuilder(activity as Activity)
        builder.setTitle(getString(R.string.clear_settings))

        builder.setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { dialogInterface, which ->
            Toast.makeText(
                activity,
                R.string.successful, Toast.LENGTH_LONG
            ).show()

            requireContext().getSharedPreferences(SCW_PREFERENCES, 0).edit { clear() }
            Toast.makeText(
                context,
                requireContext().resources.getText(R.string.app_restart),
                Toast.LENGTH_LONG
            ).show()
        }
        builder.setNegativeButton(resources.getString(R.string.cancel)) { dialogInterface, which ->

        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
    }

    private fun exportSettings(uri: Uri) {
        try {
            val userPref = requireActivity().getSharedPreferences(SCW_PREFERENCES, 0)
            val allEntries: Map<String, *> = userPref.all
            
            // Create JSON object with metadata
            val jsonObj = JSONObject()
            jsonObj.put("_version", 1) // Version for migration handling
            jsonObj.put("_timestamp", System.currentTimeMillis())
            
            for (entry in allEntries.entries) {
                // Skip internal keys
                if (entry.key.startsWith("_")) continue
                
                when (val value = entry.value) {
                    is Boolean -> jsonObj.put(entry.key, value)
                    is Int -> jsonObj.put(entry.key, value)
                    is Long -> jsonObj.put(entry.key, value)
                    is Float -> jsonObj.put(entry.key, value)
                    is String -> jsonObj.put(entry.key, value)
                    else -> jsonObj.put(entry.key, value.toString())
                }
            }

            val output: OutputStream? = requireActivity().contentResolver.openOutputStream(uri)
            output?.use {
                it.write(jsonObj.toString(2).toByteArray()) // Pretty print with indent
                it.flush()
            }
            
            Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ImportExportSettings", "Error exporting settings", e)
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettings(uri: Uri) {
        try {
            val input: InputStream? = requireActivity().contentResolver.openInputStream(uri)
            input?.use { stream ->
                val content = stream.bufferedReader().use { it.readText() }

                // Try to parse as JSON
                val jsonObj = try {
                    JSONObject(content)
                } catch (e: Exception) {
                    // Try legacy format: {"key"="value",...}
                    parseLegacySettings(content)
                }

                val userPref = requireActivity().getSharedPreferences(SCW_PREFERENCES, 0)
                val allPrefs = userPref.all

                // Get version for migration
                val version = try {
                    jsonObj.optInt("_version", 0)
                } catch (e: Exception) {
                    0 // Legacy or no version
                }

                // Apply settings with migration
                with(userPref.edit()) {
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()

                        // Skip metadata keys
                        if (key.startsWith("_")) continue

                        // Apply migrations if needed
                        val migratedKey = migrateSettingKey(key, version) ?: continue

                        // Get value from JSON
                        val jsonValue = jsonObj.get(key)
                        if (jsonValue == null || jsonValue == JSONObject.NULL) continue

                        // Determine target type based on existing preference
                        val existingValue = allPrefs[migratedKey]

                        when (existingValue) {
                            is Boolean -> {
                                when (jsonValue) {
                                    is Boolean -> putBoolean(migratedKey, jsonValue)
                                    is String -> putBoolean(migratedKey, jsonValue.toBoolean())
                                    is Int -> putBoolean(migratedKey, jsonValue != 0)
                                    else -> Log.w("ImportExportSettings", "Skipping boolean key $migratedKey due to incompatible JSON type: ${jsonValue.javaClass}")
                                }
                            }
                            is Int -> {
                                when (jsonValue) {
                                    is Int -> putInt(migratedKey, jsonValue)
                                    is Long -> putInt(migratedKey, jsonValue.toInt())
                                    is String -> putInt(migratedKey, jsonValue.toIntOrNull() ?: 0)
                                    else -> Log.w("ImportExportSettings", "Skipping int key $migratedKey due to incompatible JSON type: ${jsonValue.javaClass}")
                                }
                            }
                            is Long -> {
                                when (jsonValue) {
                                    is Long -> putLong(migratedKey, jsonValue)
                                    is Int -> putLong(migratedKey, jsonValue.toLong())
                                    is String -> putLong(migratedKey, jsonValue.toLongOrNull() ?: 0L)
                                    else -> Log.w("ImportExportSettings", "Skipping long key $migratedKey due to incompatible JSON type: ${jsonValue.javaClass}")
                                }
                            }
                            is Float -> {
                                when (jsonValue) {
                                    is Double -> putFloat(migratedKey, jsonValue.toFloat())
                                    is Float -> putFloat(migratedKey, jsonValue)
                                    is Int -> putFloat(migratedKey, jsonValue.toFloat())
                                    is String -> putFloat(migratedKey, jsonValue.toFloatOrNull() ?: 1.0f)
                                    else -> Log.w("ImportExportSettings", "Skipping float key $migratedKey due to incompatible JSON type: ${jsonValue.javaClass}")
                                }
                            }
                            is String -> {
                                putString(migratedKey, jsonValue.toString())
                            }
                            null -> {
                                // For new keys (not in existing prefs), infer type.
                                // But be careful with known String keys that might look like numbers.
                                if (isKnownStringKey(migratedKey)) {
                                    putString(migratedKey, jsonValue.toString())
                                } else {
                                    when (jsonValue) {
                                        is Boolean -> putBoolean(migratedKey, jsonValue)
                                        is Int -> putInt(migratedKey, jsonValue)
                                        is Long -> putLong(migratedKey, jsonValue)
                                        is Double -> putFloat(migratedKey, jsonValue.toFloat())
                                        is String -> {
                                            // Legacy inference or String values
                                            when {
                                                jsonValue.equals("true", ignoreCase = true) || jsonValue.equals("false", ignoreCase = true) ->
                                                    putBoolean(migratedKey, jsonValue.toBoolean())
                                                jsonValue.matches("-?\\d+".toRegex()) -> {
                                                    val longValue = jsonValue.toLongOrNull()
                                                    if (longValue != null) {
                                                        if (longValue > Int.MAX_VALUE || longValue < Int.MIN_VALUE) {
                                                            putLong(migratedKey, longValue)
                                                        } else {
                                                            putInt(migratedKey, longValue.toInt())
                                                        }
                                                    } else {
                                                        putString(migratedKey, jsonValue)
                                                    }
                                                }
                                                jsonValue.matches("-?\\d+\\.\\d+".toRegex()) ->
                                                    // Default to float for decimal numbers as SharedPreferences doesn't support Double natively
                                                    putFloat(migratedKey, jsonValue.toFloatOrNull() ?: 1.0f)
                                                else ->
                                                    putString(migratedKey, jsonValue)
                                            }
                                        }
                                        else -> putString(migratedKey, jsonValue.toString())
                                    }
                                }
                            }
                            else -> {
                                // In case of Set<String> or other types we don't explicitly handle yet
                                Log.w("ImportExportSettings", "Unknown type for key $migratedKey: ${existingValue.javaClass}")
                            }
                        }
                    }
                    apply()
                }

                // Show success and restart
                Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
                requireActivity().recreate()
            }
        } catch (e: Exception) {
            Log.e("ImportExportSettings", "Error importing settings", e)
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Parse legacy settings format: {"key"="value",...}
     */
    private fun parseLegacySettings(content: String): JSONObject {
        val jsonObj = JSONObject()
        
        // Remove surrounding braces
        val trimmed = content.trim().removeSurrounding("{", "}")
        
        // Split by comma
        val pairs = trimmed.split(",")
        
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("\"")
                val value = parts[1].trim().removeSurrounding("\"")
                jsonObj.put(key, value)
            }
        }
        
        return jsonObj
    }
    
    /**
     * Migrate setting keys from old versions to new versions
     * Returns null if setting is obsolete and should be skipped
     */
    private fun migrateSettingKey(key: String, fromVersion: Int): String {
        // Add migrations here as needed
        // Example:
        // if (fromVersion < 1) {
        //     if (key == "old_key_name") return "new_key_name"
        // }
        
        // For now, just return the same key
        return key
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri: Uri? = data?.data
        if (requestCode == EXPORT_BOOKMARKS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                exportBookmarks(uri)
            }
        } else if (requestCode == IMPORT_BOOKMARKS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                importBookmarks(uri)
            }
        } else if (requestCode == EXPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                exportSettings(uri)
            }
        } else if (requestCode == IMPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                importSettings(uri)
            }
        }
    }

    private fun isKnownStringKey(key: String): Boolean {
        // List of keys strictly known to be Strings to prevent incorrect type inference
        return key == UserPreferences.CUSTOM_SEARCH_ENGINE_URL ||
                key == UserPreferences.HOMEPAGE_URL ||
                key == UserPreferences.HOMEPAGE_BACKGROUND_URL ||
                key == UserPreferences.COLLECTION_NAME ||
                key == UserPreferences.COLLECTION_USER ||
                key == UserPreferences.BAR_ADDONS_LIST
    }

    companion object {
        const val EXPORT_BOOKMARKS = 0
        const val IMPORT_BOOKMARKS = 1
        const val EXPORT_SETTINGS = 2
        const val IMPORT_SETTINGS = 3

        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_FOLDER = "folder"

        const val SCW_PREFERENCES = "scw_preferences"
    }
}
