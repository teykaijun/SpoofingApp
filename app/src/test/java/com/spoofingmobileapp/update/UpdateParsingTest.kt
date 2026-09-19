package com.spoofingmobileapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun comparesNumerically() {
        assertTrue(Version.parse("1.2.0")!! > Version.parse("1.1.9")!!)
        assertTrue(Version.parse("v1.10.0")!! > Version.parse("1.9.0")!!)
        assertTrue(Version.parse("1.1")!! < Version.parse("1.1.1")!!)
        assertEquals(0, Version.parse("v1.1.0")!!.compareTo(Version.parse("1.1.0")!!))
    }

    @Test
    fun ignoresSuffixesAndRejectsNonNumbers() {
        assertEquals(0, Version.parse("v2.0.0-beta1")!!.compareTo(Version.parse("2.0.0")!!))
        assertNull(Version.parse("nightly"))
        assertNull(Version.parse(""))
        assertNull(Version.parse(null))
    }
}

class ParseLatestReleaseTest {

    private fun release(
        tag: String = "v1.2.0",
        assetName: String = "LocationSpoofer-v1.2.0-debug.apk",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = """
        {
          "tag_name": "$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "html_url": "https://github.com/teykaijun/SpoofingApp/releases/tag/$tag",
          "assets": [
            { "name": "LocationSpoofer-windows.zip", "size": 65000000,
              "browser_download_url": "https://example.test/windows.zip" },
            { "name": "$assetName", "size": 20000000,
              "browser_download_url": "https://example.test/app.apk" }
          ]
        }
    """.trimIndent()

    @Test
    fun findsTheApkOfANewerRelease() {
        val info = parseLatestRelease(release(), currentVersionName = "1.1.0")

        assertEquals("1.2.0", info?.versionName)
        assertEquals("https://example.test/app.apk", info?.apkUrl)
        assertEquals(20_000_000L, info?.sizeBytes)
        assertEquals(
            "https://github.com/teykaijun/SpoofingApp/releases/tag/v1.2.0",
            info?.releaseUrl,
        )
    }

    @Test
    fun ignoresReleasesThatAreNotNewer() {
        assertNull(parseLatestRelease(release(tag = "v1.1.0"), currentVersionName = "1.1.0"))
        assertNull(parseLatestRelease(release(tag = "v1.0.9"), currentVersionName = "1.1.0"))
    }

    @Test
    fun ignoresDraftsAndPreReleases() {
        assertNull(parseLatestRelease(release(draft = true), currentVersionName = "1.1.0"))
        assertNull(parseLatestRelease(release(prerelease = true), currentVersionName = "1.1.0"))
    }

    @Test
    fun ignoresReleasesWithoutAnApk() {
        val noApk = parseLatestRelease(release(assetName = "notes.txt"), currentVersionName = "1.1.0")
        assertNull(noApk)
    }

    @Test
    fun survivesRubbishInput() {
        assertNull(parseLatestRelease("not json", currentVersionName = "1.1.0"))
        assertNull(parseLatestRelease("{}", currentVersionName = "1.1.0"))
    }
}
